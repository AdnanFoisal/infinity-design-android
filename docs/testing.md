# Testing

## Test matrix

| Module | Test type | What we cover |
|---|---|---|
| `:core` | Unit | SafeMath, DeterministicRandom (reproducibility), ColorUtil (parse, round-trip), NoiseField (range, determinism) |
| `:design` | Unit | DesignDocumentCodec (round-trip, malformed, forward-compatible), DesignValidator (NaN, bad color, dup IDs, unknown effect), Migrator, History (undo/redo/coalesce/locked), LayoutEngine (out-of-bounds detection, maxWidth clamp), CandidateGenerator (sorted scores, hard rejection), Compositions (registry completeness), TypographyEngine (autoFit, measure) |
| `:graphics` | Unit | Renderer determinism (same seed → same ops), every effect renders without throwing, NaN guard, layered background emits layers |
| `:generation` | Unit | SsrfGuard rejects metadata/loopback/private IPs, allows public hostnames; BlueprintPrompts structure |
| `:backend` | Unit + Integration | RequestValidator (empty prompt, missing fields), parseBlueprint (clean JSON, markdown-fenced, missing palette) |
| `:data` | Robolectric | ProjectRepository save/load round-trip, migration path, corruption recovery |
| `:export` | Robolectric | PngExporter memory budget check, SvgExporter XML validity, PdfExporter page size, ProjectExporter round-trip equivalence |
| `:app` | Robolectric + Compose UI | EditorScreen rendering, SettingsScreen ping button, Navigation routes |

## Property-based tests

Where practical, we test invariants across random inputs:

- For every valid DesignDocument:
  - all coordinates are finite
  - dimensions are positive
  - colors are valid
  - element IDs are unique
  - serialization round-trip is stable
  - rendering does not throw

These run as parameterized JUnit tests with `DeterministicRandom` seeds.

## Fuzz tests

`FuzzTests` exercise:

- malformed JSON (truncated, missing braces, deeply nested)
- huge values (Float.MAX_VALUE, Float.MIN_VALUE)
- missing fields
- unknown fields (forward-compat test)
- nulls
- deeply nested groups (depth 64+)
- strange Unicode (RTL marks, zero-width, control chars)
- long strings (>100KB)
- weird colors (`#Z`, `rgb(999,0,0)`)
- invalid IDs (empty, with spaces)

The app must reject or sanitize every one without crashing.

## Golden image tests

`HeadlessRenderer` records draw operations. A test fixture design with a fixed seed produces a deterministic op sequence. We hash that sequence and compare against a known-good baseline. Any renderer / layout engine change that perturbs the sequence is caught.

The mechanism is simpler than pixel-diff (no image rendering required) but catches the same class of regression.

## Manual / Instrumentation tests

Section 68: instrumentation tests cover real Android behavior:

- Compose screens render without throwing
- Navigation flows work
- Project save/load on a real device
- Editor interactions (select, move, resize, undo)
- File import/export via Storage Access Framework
- Network loss during generation
- Large designs (200+ elements)
- Rotation / configuration change during edit

These are scaffolded in `:app:androidTest` and should be run on a real Android 13 ARM64 device.

## Performance benchmarks

Baseline numbers, captured locally on a mid-range Android 13 ARM64 device:

| Operation | Target |
|---|---|
| App cold start | < 1.5 s |
| Blank canvas render | < 50 ms |
| Typical poster render | < 200 ms |
| 200-element document render | < 1 s |
| 500-element document render | < 3 s |
| Local reroll | < 500 ms |
| PNG export (1x) | < 500 ms |
| PNG export (4x) | < 4 s |
