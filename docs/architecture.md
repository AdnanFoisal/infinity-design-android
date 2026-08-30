# Architecture

This document describes the modular architecture of Infinity Design.

## Module graph

```
                          +--------+
                          | :core  |  ← pure-JVM, no Android
                          +---+----+
                              │
        +---------------------+---------------------+
        │                     │                     │
        ▼                     ▼                     ▼
   +---------+          +---------+         +----------------+
   | :design |          |:graphics|         |  :generation   |
   +---------+          +---------+         +----------------+
        │                     │                     │
        └──────────────┬──────┘                     │
                       │                            │
                       ▼                            ▼
              +----------------+          +----------------+
              |    :backend    |          | (talked to by  |
              | (Ktor server) |          |  :app via HTTP)|
              +----------------+          +----------------+
                       │
                       │
        +──────────────┼──────────────┐
        ▼              ▼              ▼
   +---------+    +---------+    +---------+
   |  :data  |    | :export |    |  :app   |
   +---------+    +---------+    +---------+
   (Android)      (Android)      (Android, Compose UI)
```

Pure-JVM modules (`:core`, `:design`, `:graphics`, `:generation`, `:backend`) are tested with the JVM JUnit runner.

Android-only modules (`:data`, `:export`, `:app`) are tested with Robolectric or instrumentation.

## Key design rules

1. **Compose UI never mutates the Design Document directly.** All mutations go through commands ([DesignCommand](../design/src/main/java/com/adnanfoisal/infinitydesign/design/commands/DesignCommand.kt)).
2. **The renderer never knows about Compose.** The SkiaRenderer is platform-neutral — `:app` provides the AndroidCanvasSurface implementation.
3. **The design engine never makes LLM calls.** Stage 1 (LLM) is owned by the backend / generation providers; Stage 2 (compilation) is owned by the candidate generator.
4. **Every numeric entering the renderer is finite.** SafeMath throws on NaN/Infinity.
5. **Every external input is validated.** Imported projects, decoded LLM responses, user inputs — all pass through `DesignValidator`.

## Why these splits?

- The JVM-only modules let us run the entire design pipeline (DSL → layout → candidates → render-to-headless-surface → fuzz tests) on the CI JVM without Android.
- The Android-only modules wrap that logic in the platform APIs (Compose, Room, Canvas).
- The backend is a separate Ktor application — it's *not* embedded in the APK. The Android app talks to it over HTTP, OR uses a local fallback blueprint builder when no backend is configured.

## Process boundaries

- **App process:** owns the Design Document, the editor state, the Room database.
- **Backend process:** owns the LLM provider credentials, the prompt engineering, the rate limiting.
- **LLM provider:** external (Google Gemini API or user's LiteLLM proxy).

The backend holds user API keys only transitively — for the duration of a single request. Keys are never persisted, never logged. The Android Settings screen makes this explicit to the user.
