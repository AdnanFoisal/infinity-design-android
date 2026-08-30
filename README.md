# Infinity Design — Native Android Procedural AI Design Studio

> **The LLM is the art director. The design engine is the compositor. The graphics renderer creates the artwork. The user owns and edits the resulting design document.**

Infinity Design is a native Android application that transforms a natural-language prompt into a beautiful, fully-editable procedural design — **without** depending on an image-generation model. The LLM produces only creative direction; a deterministic design engine compiles the final artwork and the user can edit every pixel.

This is a **re-imagined native Android implementation** of the concepts in the reference Next.js project at <https://github.com/AdnanFoisal/infinity-design>. The reference implementation is a web app; this project is a fresh, native Android architecture that preserves the successful product ideas while rebuilding the engineering foundation.

---

## Table of Contents
1. [What this app does](#what-this-app-does)
2. [Architecture](#architecture)
3. [The generation pipeline](#the-generation-pipeline)
4. [Setup](#setup)
5. [Build instructions](#build-instructions)
6. [GitHub Actions & APK artifacts](#github-actions--apk-artifacts)
7. [Design DSL](#design-dsl)
8. [Procedural graphics engine](#procedural-graphics-engine)
9. [Security](#security)
10. [Testing](#testing)
11. [Limitations](#limitations)
12. [License](#license)
13. [Acknowledgements](#acknowledgements)

---

## What this app does

1. **User enters a prompt.**
   Example: *"Create a futuristic robotics competition poster for university students. Use a dark technological aesthetic, strong cyan lighting, a giant title, technical linework, and a dramatic abstract robotic visual."*

2. **Stage 1 — Creative Direction.** An LLM (Gemini or LiteLLM-backed OpenAI-compatible) transforms the prompt into a *Design Blueprint* — a structured creative intent describing mood, palette, typography, composition family, texture, density, and required content. The user reviews this in the **Direction** screen and can regenerate as many times as they want.

3. **Stage 2 — Design Compilation.** A deterministic/procedural design engine picks a composition family, measures typography using real font metrics, generates procedural graphical elements (cloud fields, gradients, grids, halftone, glow, ink splash…), produces multiple candidate layouts, scores them on text overflow / out-of-bounds / hierarchy / balance, and selects the strongest valid result.

4. **Editor.** The user owns the resulting Design Document: select, move, resize, rotate, group, undo/redo, export to PNG / SVG / PDF / project JSON. All local — no LLM call needed for edit / reroll / export.

---

## Architecture

Modular Gradle project, ~10 modules. Compose UI is independent from the design engine — the engine never knows about Compose.

```
core        — numerics safety, results, logging, dispatchers, noise, RNG (pure-JVM, tested)
design      — Design DSL, validator, migrations, commands, history, typography, layout, composition, candidate scoring (pure-JVM, tested)
graphics    — Skia-backed renderer (platform-neutral interface), procedural effects registry (pure-JVM, tested)
generation  — blueprint models, LLM provider abstraction, prompt engineering, local blueprint builder, SSRF guard (pure-JVM, tested)
backend     — Ktor server with /api/ping, /api/blueprint/generate, Gemini + LiteLLM providers, rate limiting, structured errors (pure-JVM, tested)
data        — Room database, DataStore, project repository, file import/export (Android-only)
export      — PNG, SVG, PDF, project JSON exporters, Android Canvas Surface (Android-only)
app         — Compose UI: HomeScreen, GenerationScreen, DirectionScreen, EditorScreen, SettingsScreen
```

See [docs/architecture.md](docs/architecture.md) for the full module diagram and design rules.

---

## The generation pipeline

```
Prompt
  │
  ▼
LLM (Stage 1) → Design Blueprint
  │                  ▲ regenerate (different seed)
  ▼
Direction Screen ────┘
  │ accept
  ▼
Candidate Generator
  ├── picks composition family
  ├── varies spacing / typography / procedural seed
  ├── measures typography (real StaticLayout on Android)
  └── produces N candidates
  │
  ▼
Quality Scorer
  ├── hard constraints (NaN, out-of-bounds, missing required content)
  └── soft constraints (hierarchy, balance, density)
  │
  ▼
Best candidate → Design Document → render → edit → export
```

After Stage 1 has produced a blueprint, all subsequent operations (reroll, edit, save, reopen, export, render) work **offline** — section 108.

---

## Setup

### Prerequisites
- **Android Studio** (latest stable, e.g. Ladybug or newer)
- **JDK 17+** (the project uses AGP 8.7.2 + Kotlin 2.0.21)
- **Android SDK 35** (compile target) — installed automatically by GitHub Actions
- **Android 13+ physical device or emulator** (the spec's primary test device)

### Quick start
```bash
# Clone
git clone https://github.com/<your-org>/infinity-design.git
cd infinity-design

# Verify the Gradle wrapper
./gradlew --version

# Build everything
./gradlew build

# Run unit tests for pure-JVM modules
./gradlew :core:test :design:test :graphics:test :generation:test :backend:test
```

---

## Build instructions

### Debug APK (ARM64 only — section 95)
```bash
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

### Release APK (unsigned, ARM64 only)
```bash
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk
```

To sign a release build locally, configure your keystore in `~/.gradle/gradle.properties`:
```
INFINITY_KEYSTORE=/path/to/release.jks
INFINITY_KEY_ALIAS=release
INFINITY_KEY_PASSWORD=...
INFINITY_STORE_PASSWORD=...
```
…and uncomment the `signingConfigs` block in `app/build.gradle.kts`. **Never commit your keystore.**

### Backend (server-side, optional — the app can run offline)
```bash
./gradlew :backend:run
# → starts Ktor on http://localhost:8080
```

---

## GitHub Actions & APK artifacts

The workflow at `.github/workflows/build.yml`:
- runs on every push and pull request,
- sets up JDK 17 + Android SDK 35,
- compiles, lints, runs all unit tests,
- builds an `arm64-v8a`-only debug APK,
- verifies the APK actually contains only `lib/arm64-v8a/` (no x86/armeabi leaks),
- uploads the APK as a downloadable artifact (`infinity-design-arm64-debug`),
- on tag pushes (`v*`), also builds an unsigned release APK,
- scans the repository for accidentally committed secrets (`ghp_…`, `sk-…`, `AIza…`, `AKIA…`) and fails the build if any are found.

Download the APK from the **Actions** tab → click the latest run → **Artifacts** → `infinity-design-arm64-debug`.

---

## Design DSL

The `:design` module exposes a strongly-typed, versioned Design Document. See [docs/design-dsl.md](docs/design-dsl.md) for the full schema.

- Every document has a `schemaVersion`. Older projects migrate forward, never silently destroyed.
- All numbers entering the renderer are checked for NaN/Infinity (section 47).
- Element hierarchy: Text, Shape, Procedural, Image, Group.
- Constraints: anchor, max-width/height, aspect-ratio, spacing, safe-zone.

---

## Procedural graphics engine

The `:graphics` module ships 24 procedural primitives:

- **Atmospheric**: CloudField, AuroraField, SoftLightField, VolumetricGlow
- **Organic**: FluidBlob, LiquidGradient, InkSplash, BlobField
- **Texture**: Grain, PaperTexture, Halftone, Speckle, MeshTexture
- **Technical**: Grid, Circuit, TechnicalLines, BlueprintLines, Rings
- **Abstract**: Rays, WaveField, ParticleField, OrbitSystem, DistortionField
- **Glow**: Glow, VolumetricGlow

Every effect is **parametric** — the same seed reproduces the same pixels. See [docs/procedural-graphics.md](docs/procedural-graphics.md).

---

## Security

- **No secrets in the repo.** The CI secret scan refuses any commit containing `ghp_*`, `sk-*`, `AIza*`, or `AKIA*`.
- **SSRF protection.** LiteLLM URLs are validated to reject loopback / link-local / private IPs unless explicitly opted in.
- **BYOK.** API keys are kept in-memory only for the duration of a request. They are never persisted, never logged, never included in analytics.
- **Renderer safety.** Every numeric input is checked for NaN/Infinity. Effect names are allowlisted. Colors are validated.
- **Import safety.** Project JSON is untrusted — validated, migrated, and never executed.

See [docs/security.md](docs/security.md).

---

## Testing

The project ships substantial automated tests:

| Module | What we test |
|---|---|
| `:core` | SafeMath, DeterministicRandom (seed reproducibility), ColorUtil parsing, NoiseField range |
| `:design` | DSL serialization round-trip, validator (rejects NaN/bad colors/duplicate IDs), migrator, history (undo/redo/coalesce/locked), layout, candidate generator (sorted scores), compositions registry |
| `:graphics` | Renderer determinism (same seed → same ops), every procedural effect renders without throwing |
| `:generation` | SSRF guard rejects metadata/loopback/private IPs; blueprint JSON parser handles markdown-fenced output |
| `:backend` | RequestValidator, blueprint parsing for clean + malformed input |

Property-based tests and fuzz tests live in [docs/testing.md](docs/testing.md).

---

## Limitations

Documented honestly (section 27 of the spec):

- **Editor is a first version.** Multi-touch rotation and multi-select drag are scaffolded but the production polish (snapping guides, alignment marquee, keyboard support on tablets) is ongoing.
- **Backend BYOK key flow.** The Android app sends the user's key to the backend over HTTPS; the backend forwards it to the LLM provider. **The key does reach the server transitively.** The documentation in SettingsScreen makes this clear to the user. For a true "key never reaches server" mode, run the backend locally on the user's own machine and configure `litellmUrl` to point at it.
- **Custom path shapes** (`ShapeKind.CUSTOM_PATH`) currently fall back to a rectangle — full path DSL support is on the roadmap.
- **Asset image rendering.** Procedural placeholder is shown; user-image decode (with bounds validation, orientation preservation, caching) is on the roadmap.
- **Sign-in and cloud sync** are out of scope for v1 — the app is fully local-first.
- **Android Proguard minification** is enabled for release builds but the rules are conservative. Test a release build on a real device before shipping.

---

## License

Released under the **Apache License, Version 2.0**. See [LICENSE](LICENSE).

Third-party libraries are licensed under their respective terms (see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)).

---

## Acknowledgements

The product concept and several algorithms (Design DSL schema, prompt engineering, style presets, procedural asset registry, auto-layout, text fidelity, SVG renderer) were originally implemented in the **Next.js reference project**:

> <https://github.com/AdnanFoisal/infinity-design>

This Android project is a fresh, native re-implementation. It preserves the successful product ideas and intentionally avoids the architectural weaknesses of the reference (monolithic page.tsx, weak runtime validation, public generation endpoint, approximate text measurement). The reference repository is linked here as required by the engineering brief and as a courtesy to its authors.
