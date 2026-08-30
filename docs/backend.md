# Backend

The backend is a small Ktor service. It owns the LLM provider credentials and proxies requests — the Android app never talks to LLM providers directly.

## Endpoints

### `GET /health`
Liveness probe. Returns `"ok"` (200).

### `POST /api/ping`
Tests whether the user's provider credentials are working. Sends the smallest possible request to the configured provider and reports back.

Request:
```json
{
  "provider": "GEMINI",
  "providerConfig": {
    "geminiApiKey": "<key>",
    "geminiModel": "gemini-3.7-flash"
  }
}
```

Response (200):
```json
{
  "ok": true,
  "provider": "gemini",
  "model": "gemini-3.7-flash",
  "durationMs": 480,
  "message": "OK"
}
```

Response (4xx/5xx):
```json
{
  "error": "Gemini ping failed",
  "kind": "ProviderUnavailable",
  "detail": "HTTP 503"
}
```

### `POST /api/blueprint/generate`
Stage 1: prompt → Blueprint. Request + response shapes are defined in `:generation` module.

## Provider abstraction

```kotlin
interface LlmProvider {
    val name: String
    suspend fun generateBlueprint(request: BlueprintRequest): AppResult<BlueprintResponse>
    suspend fun ping(): AppResult<Long>
    fun isConfigured(): Boolean
}
```

Implementations:

- `GeminiProvider` — calls `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`
- `LiteLlmProvider` — calls `{litellmUrl}/v1/chat/completions` with Bearer auth

## Security

- **SSRF protection** — LiteLLM URLs are validated to reject loopback / link-local / private IPs unless explicitly opted in (`-Dinfinitydesign.litellm.allow.local=true`).
- **Rate limiting** — 60 requests / 60 seconds per IP globally, configurable.
- **Timeouts** — connect 10s, socket 15s, request 60s for blueprint generation, 20s for ping.
- **Structured errors** — every failure returns `{ error, kind, detail }` with the right HTTP status.
- **No persistent key storage** — the Android client sends the key with each request; the backend forwards it to the provider and immediately discards it.
- **No key logging** — `AppLogger.scrub()` redacts `Bearer …`, `sk-…`, `ghp_…`, `gho_…`, `AIza…` from all log output.

## Local development

```bash
./gradlew :backend:run
```

Starts on http://localhost:8080.

For local testing of the Android app against a local backend:

```bash
adb reverse tcp:8080 tcp:8080
```

Then in the Android app's Settings screen, set Backend URL to `http://localhost:8080`.
