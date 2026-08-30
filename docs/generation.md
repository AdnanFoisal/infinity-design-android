# Generation Pipeline

This document describes how a prompt becomes a Design Document.

## Stage 1 — Creative Direction (LLM)

The user enters a prompt. The app sends it to the backend at `POST /api/blueprint/generate`.

```
{
  "prompt": "Create a futuristic robotics competition poster…",
  "style": "dark technical",
  "aspectId": "portrait-poster",
  "seed": 12345,
  "provider": "GEMINI",
  "providerConfig": {
    "geminiApiKey": "<user-provided>",
    "geminiModel": "gemini-3.7-flash"
  },
  "variations": 1,
  "locale": "en"
}
```

The backend:

1. **Validates** the request — section 37. Rejects empty prompts, oversized prompts, invalid locales, missing credentials.
2. **Builds the prompt** — `BlueprintPrompts.system` + `userPrompt(inputs)`. Explicitly forbids the LLM from producing pixel coordinates.
3. **Calls the LLM** — Gemini (`/v1beta/models/{model}:generateContent`) or LiteLLM (`/v1/chat/completions`).
4. **Parses the response** — `parseBlueprint(raw, originalPrompt, elapsedMs)` handles JSON inside markdown fences, rejects missing `palette`/`typography`, returns `AppResult<BlueprintResponse>`.
5. **Validates the resulting blueprint** — `DesignValidator.validateBlueprint(bp)` rejects unknown compositions, bad colors, missing required content.
6. **Returns the validated blueprint.**

## Stage 1 fallback — Local blueprint builder

If no provider is configured (the user hasn't entered an API key yet), the app uses `LocalBlueprintBuilder`:

- Picks a palette from 8 curated sets (Dark Tech, Editorial Mono, Soft Pastel, Cyber Punk, Japanese Mono, Brutalist, Warm Earth, Cool Forest).
- Picks a composition from the 16 supported families.
- Picks a mood, density, texture list, decorative list — all seeded from the prompt + seed.
- Extracts a title from the prompt (quoted text or first sentence).

This lets the user verify the entire pipeline end-to-end before paying for an LLM.

## Direction Screen

The user reviews the blueprint on the Direction Screen:

- title, purpose, mood, visual direction
- palette swatches
- typography (display / body / caption)
- composition family
- density, texture, decorative, lighting, imagery
- hierarchy
- protected content (verbatim, never paraphrased)

Two actions:

- **Regenerate Direction** — re-runs Stage 1 with a new seed, producing a *genuinely different* art direction (section 10). The variation happens at the conceptual level (different palette + composition family), not just color tweaks.
- **Use This Direction** — proceeds to Stage 2.

## Stage 2 — Design Compilation (deterministic)

`CandidateGenerator.generateCandidates(blueprint, canvas, count = 6)`:

1. **Pick composition.** If the blueprint specifies a known composition, use it; otherwise pick from all 16.
2. **Apply the composition skeleton.** Each `Compositions.apply(canvas, bp, seed)` returns a list of elements (background procedural layer + title text + subtitle text + body text + accent shapes) at recommended bounds.
3. **Vary spacing / typography scale.** For each candidate, draw a random `scale` in `[0.85, 1.35]` and apply it to the skeleton's bounds + token sizes.
4. **Run the layout engine.** `LayoutEngine.resolve(doc)`:
   - Measures text using `AndroidTypographyEngine` (real `StaticLayout`) — section 20.
   - Applies `LayoutConstraint`s to anchor elements to parent/sibling edges.
   - Returns the resolved document + out-of-bounds element IDs.
5. **Score the candidate.** Hard violations (NaN, out-of-bounds, missing required content) reject the candidate. Soft constraints (overflow penalty, hierarchy strength, quadrant balance) deduct points.
6. **Sort + filter.** Drop candidates with hard violations. Sort by score descending.
7. **Pick the best.**

## Local Reroll (no LLM call)

Section 24: after a Design Document exists, "Reroll" must NOT call the LLM.

Reroll varies:
- procedural seed
- composition (within the blueprint's family or close neighbors)
- spacing scale
- typography scale
- texture layer opacity / seed

…all locally. The blueprint is preserved. Required content is preserved.

## Offline mode (Section 108)

After Stage 1 has produced a blueprint, the user can:
- edit
- reroll
- save
- reopen
- export
- render

without internet.
