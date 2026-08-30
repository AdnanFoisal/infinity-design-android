# Rendering

The renderer is platform-neutral. It depends only on the `DrawSurface` interface — Android provides `AndroidCanvasSurface`, tests provide `HeadlessRenderer`.

## Render pipeline

```
DesignDocument
  │
  ▼
SkiaRenderer.render(doc, surface, quality)
  │
  ├─ renderBackground(doc, surface)
  │    └─ for each layer in BackgroundSpec.Layered:
  │        ├─ pushLayer()
  │        ├─ setOpacity / setBlendMode
  │        ├─ procedural.get(effect).render(seed, bounds, params, surface)
  │        └─ popLayer()
  │
  └─ for each element in doc.elements (top-down):
       ├─ if !visible: skip
       ├─ if !validate(): skip (defense in depth)
       ├─ save(), translate (rotation pivot), rotate
       ├─ dispatch on type (Text / Shape / Procedural / Image / Group)
       └─ restore()
```

## Quality levels

- `EDIT` — used by the editor preview. Procedural effects run at reduced density.
- `EXPORT_HIGH` — used by PNG / SVG / PDF export. Full quality.
- `THUMBNAIL` — used by the project list. Lower resolution, fewer layers.

## Defense in depth

Even though the validator catches malformed input, the renderer re-checks every coordinate:

- `SafeMath.allFinite(x, y, w, h)` before any draw call
- `SafeMath.clampSafe(alpha, 0, 1)` for opacities
- `radius.coerceAtLeast(0)` for ellipses
- Invalid effect names are silently skipped
- Invalid colors fall back to gray

This means a maliciously-constructed project JSON cannot crash the renderer — at worst it produces an unstyled design.

## Headless test renderer

`HeadlessRenderer` records draw operations instead of drawing. This lets tests assert:

- The same seed produces the same op sequence (golden-image regression basis).
- The renderer does not throw on any valid design.
- Effects don't depend on random platform state.

See `:graphics` test sources for the determinism tests.
