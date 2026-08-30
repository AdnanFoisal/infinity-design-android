# Design DSL

The Design DSL is the source of truth for everything the app renders, edits, exports.

## Two-model separation

| Model | Role | Lives where |
|---|---|---|
| `DesignBlueprint` | Creative intent from the LLM. No pixel coordinates. | `:design` / `:generation` |
| `DesignDocument` | Executable, editable design. Real coordinates, fonts, paths. | `:design` |

The blueprint is *compiled* into a document by the candidate generator — never displayed directly.

## DesignDocument schema (v1)

```kotlin
@Serializable
data class DesignDocument(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val canvas: CanvasSpec,
    val background: BackgroundSpec,
    val palette: PaletteSpec,
    val typography: TypographySpec,
    val tokens: DesignTokens,
    val elements: List<DesignElement>,
    val constraints: List<LayoutConstraint>,
    val metadata: DocumentMetadata,
    val blueprintId: String?,
    val seed: Long,
)
```

### Elements

`DesignElement` is a sealed class:

- `Text` — content, fontRole, fontSize, alignment, weight, italic, uppercase, truncate
- `Shape` — kind (Rectangle / RoundedRect / Ellipse / Triangle / Line / Polygon / Star / CustomPath), fill, stroke, cornerRadius
- `Procedural` — effect name (allowlisted), params map, seed, blendMode
- `Image` — assetId, fit (Fill / Cover / Contain / Tile), cornerRadius
- `Group` — childrenIds (logical container)

Every element carries `id`, `bounds`, `rotation`, `opacity`, `visible`, `locked`, `name`.

### Constraints

LayoutConstraint is sealed:

- `Anchor` — pin to parent or sibling edge (LEFT, RIGHT, TOP, BOTTOM, CENTER_X, CENTER_Y)
- `MaxWidth` / `MaxHeight` — clamp dimensions (fraction or pixel)
- `AspectRatio` — ratio as float
- `Spacing` — gap between siblings
- `SafeZone` — fractional safe area for all elements

### Background

- `Solid` — single color
- `LinearGradient` — multi-stop, angle
- `RadialGradient` — multi-stop, center + radius
- `Layered` — base color + procedural layers (each with effect name + seed + opacity + blend mode + bounds)

## Validation rules

`DesignValidator` rejects:

- NaN/Infinity in any coordinate
- Dimensions outside `[1, 100_000]`
- Invalid color formats (only `#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`, `rgb(...)`, `rgba(...)` accepted)
- Unknown effect names (only the registry's allowlist passes)
- Unknown blend modes
- Unknown font roles
- Duplicate element IDs
- Cyclic / overly nested groups (depth > 64)
- Text content longer than 100,000 chars

## Serialization

- Uses kotlinx-serialization JSON
- `ignoreUnknownKeys = true` (forward compatibility)
- `encodeDefaults = true` (round-trip stable)
- Round-trip: `encode → decode → encode` produces the same string.

## Migrations

`DesignDocumentMigrator` walks `1 → 2 → 3 → …` up to the latest supported version.
Each migration is a pure function on the deserialized document.
Older projects migrate forward — never silently destroyed.
