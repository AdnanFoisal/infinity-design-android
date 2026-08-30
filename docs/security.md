# Security

Infinity Design handles untrusted input (imported project JSON, LLM responses) and user-supplied credentials (Gemini / LiteLLM API keys). This document describes the defense-in-depth posture.

## 1. Never trust imported JSON

`ProjectExporter.import(raw)`:

1. Parse with `Json { ignoreUnknownKeys = true; isLenient = false }` — strict JSON, tolerant of unknown fields (forward compatibility).
2. Decode via `DesignDocumentCodec.decode` — kotlinx-serialization handles the type system; malformed JSON returns `AppResult.Err`.
3. Migrate via `DesignDocumentMigrator.migrate` — older schema versions walk forward. Future schema versions are rejected (the app refuses to open a doc newer than it understands).
4. Validate via `DesignValidator.validate` — rejects NaN/Infinity, out-of-range dimensions, bad colors, unknown effects, unknown blend modes, unknown fonts, duplicate IDs, deeply nested groups, oversized text.
5. The renderer **also** independently re-validates every coordinate before drawing — defense in depth (section 49).

## 2. Never allow NaN/Infinity

`SafeMath` is the single entry point for numeric validation:

- `requirePositive(v, name)` — throws `IllegalArgumentException` on NaN/Inf/≤0
- `requireNonNegative(v, name)` — throws on NaN/Inf/<0
- `requireFinite(v, name)` — throws on NaN/Inf
- `sanitize(v, fallback)` — non-throwing, returns fallback for NaN/Inf
- `clampSafe(v, min, max)` — clamp + sanitize in one call

Every draw call on the `DrawSurface` interface passes through these.

## 3. SSRF protection

`SsrfGuard.validate(url)` rejects:

- non-http(s) schemes
- malformed URLs
- hosts that resolve to private IP ranges (10.x, 172.16-31.x, 192.168.x, fc00::/7, fe80::/10)
- link-local addresses (169.254.x — AWS metadata endpoint)
- loopback (localhost, 127.0.0.1, ::1)

A system property `infinitydesign.litellm.allow.local=true` opts in to local-only URLs (for self-hosted LiteLLM proxy on the user's own machine).

## 4. BYOK without surprises

The Android app sends the user's API key to the backend over HTTPS. The backend:

- forwards it to the LLM provider as a Bearer token (LiteLLM) or query parameter (Gemini)
- immediately discards it after the request completes
- never persists it
- never logs it (`AppLogger.scrub` redacts it before any println)

The SettingsScreen documents this flow explicitly to the user. We do not claim "the key never reaches our server" — that would be false. The key does reach the backend transitively.

For a true "key never leaves the device" mode, the user can self-host the backend on their own machine and configure the app's Backend URL to point at it.

## 5. No secrets in the repository

- `.gitignore` excludes `*.jks`, `*.keystore`, `*.env`, `secrets/`, `*.secret`, `*.key`, `*.pem`
- The CI workflow runs a secret scan over the entire repo, refusing any commit containing `ghp_*`, `sk-*`, `AIza*`, `AKIA*`.
- The user's GitHub PAT is never used by this codebase — `git push` is the user's responsibility.
- The Android keystore for release signing is referenced by Gradle properties, not committed.

## 6. Renderer safety

Even if a malicious project JSON slips past the validator (which would itself be a bug), the renderer:

- skips elements that fail `el.validate()`
- replaces NaN/Inf with safe defaults via `SafeMath.sanitize`
- clamps opacity to `[0, 1]`
- clamps radius / fontSize to sensible ranges
- skips unknown effect names (registry lookup returns null)
- skips unknown blend modes (falls back to NORMAL)
- skips invalid color parses (falls back to gray)

This means a malicious document cannot crash the renderer — at worst it produces an unstyled design.

## 7. Network resilience

Section 41: every failure mode the app encounters has a structured error kind:

| Kind | HTTP | User-facing message |
|---|---|---|
| `NetworkUnreachable` | 503 | "Network unavailable" |
| `NetworkTimeout` | 408 | "Request timed out" |
| `ProviderUnavailable` | 502 | "Provider unavailable" |
| `ProviderRefusal` | 502 | "Provider refused the request" |
| `MalformedResponse` | 502 | "Malformed response from provider" |
| `EmptyResponse` | 502 | "Provider returned an empty response" |
| `RateLimited` | 429 | "Too many requests. Try again shortly." |
| `Unauthorized` | 401 | "Invalid API key" |
| `Forbidden` | 403 | "Access denied" |
| `Cancelled` | — | "Operation cancelled" |

The Android UI maps these to user-friendly copy (never raw stack traces).

## 8. Logging

`AppLogger.scrub` runs on every log message before it leaves the logger. It redacts:

- `Bearer <token>` → `Bearer ***REDACTED***`
- `sk-<key>` → `***REDACTED-sk***`
- `ghp_<token>` → `***REDACTED-pat***`
- `AIza<key>` → `***REDACTED-gemini***`
- `api_key: <value>` / `apiKey: <value>` → `***REDACTED***`

The pattern list is easy to extend — add a new redactor in `AppLogger.scrub` as new credential formats appear.
