# Continuous Integration

The pipeline lives at `.github/workflows/build.yml`.

## Triggers

- `push` on `main`, `master`, `release/*` — full build + APK upload
- `pull_request` — full build (no APK artifact upload)
- `workflow_dispatch` — manual trigger
- Tags matching `v*` — also build a release APK (unsigned)

## Pipeline stages

1. **Checkout** — clean checkout, `fetch-depth: 0` for tag-based versioning.
2. **Set up JDK 17** — Temurin distribution.
3. **Set up Android SDK** — `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`.
4. **Cache Gradle** — keyed by `gradle/libs.versions.toml` and `**/*.gradle*` hash.
5. **Dependency resolution** — `./gradlew dependencies` (fails fast on missing artifacts).
6. **Compile** — `./gradlew compileDebugKotlin`.
7. **Lint** — `./gradlew lintDebug --continue || true` (non-blocking, captures issues).
8. **Unit tests (pure-JVM modules)** — `:core:test :design:test :graphics:test :generation:test :backend:test`.
9. **Unit tests (Android modules with Robolectric)** — `:data:testDebug :export:testDebug :app:testDebugUnitTest`.
10. **Build debug APK** — `:app:assembleDebug`.
11. **Verify ARM64-only** — unzip the APK and grep `lib/` to confirm only `arm64-v8a/` is present.
12. **Upload APK artifact** — `infinity-design-arm64-debug`, 30-day retention.
13. **Build release APK** (tag pushes only) — `:app:assembleRelease`.
14. **Upload release APK** (tag pushes only) — `infinity-design-arm64-release-unsigned`, 90-day retention.
15. **Secret scan** — `grep -RE 'ghp_…|sk-…|AIza…|AKIA…' . ; fail if any match`.

## APK artifact

The uploaded APK is named `app-arm64-v8a-debug.apk` and lives at `app/build/outputs/apk/debug/`.

To install on a real Android 13 ARM64 device:

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

## Signing

Section 94: never commit signing keys.

For local release signing, configure in `~/.gradle/gradle.properties`:

```
INFINITY_KEYSTORE=/absolute/path/to/release.jks
INFINITY_KEY_ALIAS=release
INFINITY_KEY_PASSWORD=...
INFINITY_STORE_PASSWORD=...
```

…then uncomment the `signingConfigs` block in `app/build.gradle.kts`.

For CI release signing (not yet implemented), use GitHub Actions secrets — never commit the keystore.

## Backend artifact

The pipeline also builds the backend fat JAR via `:backend:installDist` and uploads it as `infinity-design-backend` (14-day retention).

This is the Ktor server you can run locally to test the LLM provider integration without deploying.
