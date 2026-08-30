# Procedural Graphics Engine

The procedural engine is the heart of Infinity Design. It produces visually rich backgrounds without an image-generation model.

## Effect catalog

Every effect is a `ProceduralEffect`:

```kotlin
interface ProceduralEffect {
    val name: String
    fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface)
}
```

The `ProceduralRegistry` registers all 24 effects. The validator allowlists every effect name — unknown names are rejected.

## Categories

| Category | Effects |
|---|---|
| Atmospheric | `cloud`, `aurora`, `soft_light`, `volumetric_glow` |
| Organic | `fluid_blob`, `liquid_gradient`, `ink_splash`, `blob_field` |
| Texture | `grain`, `paper`, `halftone`, `speckle`, `mesh_texture` |
| Technical | `grid`, `circuit`, `technical_lines`, `blueprint_lines`, `rings` |
| Abstract | `rays`, `wave_field`, `particle_field`, `orbit_system`, `distortion_field` |
| Glow | `glow`, `volumetric_glow` |

## Parametric, not bitmap

Each effect is parametric. Example: a CloudField layer stores:

```json
{
  "effect": "cloud",
  "seed": 18273,
  "opacity": 0.7,
  "blendMode": "normal",
  "bounds": { "x": 0, "y": 0, "width": 1080, "height": 1620 },
  "params": {
    "palette": ["#3F51B5", "#7986CB"],
    "scale": 0.72,
    "turbulence": 0.55,
    "softness": 0.8,
    "octaves": 4
  }
}
```

Same seed + same params → same pixels. Re-rolling the seed gives a different but coherent variation.

## How the noise works

`NoiseField` (in `:core`) provides:

- `gradient(seed, x, y)` — Perlin-style 2D gradient noise, range `[-1, 1]`
- `fbm(seed, x, y, octaves, lacunarity, gain)` — fractal brownian motion, normalized to `[-1, 1]`
- `worley(seed, x, y, cellSize)` — Worley/Voronoi nearest-distance, range `[0, 1]`

All noise uses a stable integer-lattice hash — no `java.util.Random`, no platform dependencies. Identical on every Android device and on the JVM test runner.

## Param support

`ParamSupport` helpers in `:graphics`:

- `float(params, "key", default)` — sanitize NaN/Infinity, return default on parse failure
- `int(params, "key", default)` — bounded to `[1, 10000]`
- `color(params, "key", default)` — parse via `ColorUtil.parse`, fall back to default on failure
- `list(params, "key", default)` — comma-separated list, capped at 20 entries

## Combining effects (Section 86)

A `BackgroundSpec.Layered` lets you stack effects. Example for a futuristic poster:

```json
{
  "type": "layered",
  "base": "#0F172A",
  "layers": [
    { "effect": "cloud", "opacity": 0.7, "seed": 1, "params": { "palette": ["#3F51B5", "#7986CB"] } },
    { "effect": "rays", "opacity": 0.5, "seed": 2, "params": { "color": "#FFE082", "count": 12 } },
    { "effect": "grid", "opacity": 0.4, "seed": 3, "params": { "color": "#00E5FF", "spacing": 40, "lineWidth": 0.5 } },
    { "effect": "grain", "opacity": 0.1, "seed": 4, "params": { "intensity": 0.08 } }
  ]
}
```

## Selection discipline (Section 87)

The candidate generator selects effects intentionally based on the blueprint's `density`:

- `MINIMAL` → 1 effect
- `BALANCED` → 2 effects
- `RICH` → 3 effects
- `DENSE` → 4 effects

A minimal poster may use one gradient + one shape + typography. A cyberpunk poster may stack atmosphere + glow + grid + technical lines. The blueprint controls density, not the renderer.
