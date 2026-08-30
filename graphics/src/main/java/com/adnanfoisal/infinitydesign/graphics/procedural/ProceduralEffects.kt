package com.adnanfoisal.infinitydesign.graphics.procedural

import com.adnanfoisal.infinitydesign.core.util.DeterministicRandom
import com.adnanfoisal.infinitydesign.core.util.NoiseField
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.graphics.renderer.DrawSurface
import com.adnanfoisal.infinitydesign.graphics.renderer.DrawPath
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A procedural graphics primitive. Same seed + same bounds + same params = same pixels.
 *
 * Section 11/12: every effect is parametric, not a static bitmap.
 * Section 13: rendering stays deterministic.
 */
interface ProceduralEffect {
    val name: String
    fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface)
}

object ParamSupport {
    fun float(params: Map<String, JsonElement>, key: String, default: Float): Float {
        val v = (params[key] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: return default
        return SafeMath.sanitize(v, default)
    }
    fun int(params: Map<String, JsonElement>, key: String, default: Int): Int {
        val v = (params[key] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: return default
        return if (v in 1..10_000) v else default
    }
    fun color(params: Map<String, JsonElement>, key: String, default: String): String =
        (params[key] as? JsonPrimitive)?.content?.let {
            try { com.adnanfoisal.infinitydesign.core.util.ColorUtil.parse(it); it } catch (_: Throwable) { default }
        } ?: default

    fun list(params: Map<String, JsonElement>, key: String, default: List<String>): List<String> =
        (params[key] as? JsonPrimitive)?.content?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.take(20)
            ?: default
}

class ProceduralRegistry {
    private val effects: MutableMap<String, ProceduralEffect> = LinkedHashMap()

    init {
        register(CloudField)
        register(AuroraField)
        register(SoftLightField)
        register(FluidBlob)
        register(LiquidGradient)
        register(InkSplash)
        register(BlobField)
        register(Grain)
        register(PaperTexture)
        register(Halftone)
        register(Speckle)
        register(Grid)
        register(Circuit)
        register(TechnicalLines)
        register(BlueprintLines)
        register(Rings)
        register(Rays)
        register(WaveField)
        register(ParticleField)
        register(OrbitSystem)
        register(Glow)
        register(VolumetricGlow)
        register(MeshTexture)
        register(DistortionField)
    }

    fun register(e: ProceduralEffect) { effects[e.name] = e }
    fun get(name: String): ProceduralEffect? = effects[name]
    fun all(): List<ProceduralEffect> = effects.values.toList()
    fun names(): List<String> = effects.keys.toList()
}

// ====== Atmospheric effects ======

object CloudField : ProceduralEffect {
    override val name = "cloud"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val palette = ParamSupport.list(params, "palette", listOf("#3F51B5", "#7986CB"))
        val scale = ParamSupport.float(params, "scale", 0.7f)
        val turbulence = ParamSupport.float(params, "turbulence", 0.55f)
        val softness = ParamSupport.float(params, "softness", 0.8f)
        val opacity = ParamSupport.float(params, "opacity", 0.7f)
        val octaves = ParamSupport.int(params, "octaves", 4)
        if (!SafeMath.allFinite(scale, turbulence, softness, opacity)) return

        // Clouds are rendered as a series of soft ellipses seeded by noise samples.
        // The "cloudy" look comes from many overlapping low-opacity ellipses.
        val r = DeterministicRandom(seed)
        val bandHeight = bounds.height / 8f
        surface.setOpacity(SafeMath.clampSafe(opacity, 0f, 1f))
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.SCREEN)
        for (i in 0 until 80) {
            val t = i / 80f
            val nx = bounds.x + bounds.width * t + NoiseField.gradient(seed, t * scale * 4f, 0f) * bounds.width * 0.1f
            val ny = bounds.y + bounds.height * 0.5f + NoiseField.gradient(seed xor 11L, t * scale * 4f, 1f) * bandHeight
            val radius = bounds.height.coerceAtLeast(1f) * (0.2f + NoiseField.fbm(seed + i, t * scale * 4f, 0f, octaves, 2f, 0.5f) * 0.3f)
            val color = palette[r.nextInt(palette.size)]
            val c = com.adnanfoisal.infinitydesign.core.util.ColorUtil.parse(color)
            val alpha = (radius / bounds.height.coerceAtLeast(1f)).coerceIn(0f, 1f) * softness
            surface.setOpacity(SafeMath.clampSafe(alpha * opacity, 0f, 1f))
            surface.drawEllipse(nx, ny, radius, radius * 0.6f, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
        surface.setOpacity(1f)
    }
}

object AuroraField : ProceduralEffect {
    override val name = "aurora"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val palette = ParamSupport.list(params, "palette", listOf("#00E5FF", "#9C27B0", "#3F51B5"))
        val bands = ParamSupport.int(params, "bands", 4)
        val intensity = ParamSupport.float(params, "intensity", 0.7f)
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.SCREEN)
        surface.setOpacity(SafeMath.clampSafe(intensity, 0f, 1f))
        for (i in 0 until bands) {
            val t = (i + 1) / (bands + 1).toFloat()
            val yc = bounds.y + bounds.height * t
            val bandHeight = bounds.height * 0.15f
            for (j in 0 until 40) {
                val jf = j / 40f
                val x = bounds.x + jf * bounds.width
                val noiseY = NoiseField.fbm(seed + i * 31L, jf * 4f, 0f, 4, 2f, 0.5f) * bandHeight
                val color = palette[(i + j) % palette.size]
                surface.drawEllipse(x, yc + noiseY, bounds.width * 0.06f, bandHeight * 0.6f,
                    com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
            }
        }
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
        surface.setOpacity(1f)
    }
}

object SoftLightField : ProceduralEffect {
    override val name = "soft_light"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#FFFFFF")
        val x = bounds.x + bounds.width * ParamSupport.float(params, "centerX", 0.5f)
        val y = bounds.y + bounds.height * ParamSupport.float(params, "centerY", 0.5f)
        val radius = bounds.width.coerceAtLeast(bounds.height) * ParamSupport.float(params, "radius", 0.5f)
        val intensity = ParamSupport.float(params, "intensity", 0.5f)
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.SCREEN)
        surface.setOpacity(SafeMath.clampSafe(intensity, 0f, 1f))
        // Concentric soft ellipses
        for (i in 6 downTo 1) {
            val f = i / 6f
            surface.drawEllipse(x, y, radius * f, radius * f,
                com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
        surface.setOpacity(1f)
    }
}

// ====== Organic effects ======

object FluidBlob : ProceduralEffect {
    override val name = "fluid_blob"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#7986CB")
        val complexity = ParamSupport.int(params, "complexity", 16)
        val wobble = ParamSupport.float(params, "wobble", 0.2f)
        val cx = bounds.x + bounds.width / 2f
        val cy = bounds.y + bounds.height / 2f
        val rx = bounds.width / 2f
        val ry = bounds.height / 2f
        if (!SafeMath.allFinite(cx, cy, rx, ry) || rx < 0f || ry < 0f) return
        val path = object : DrawPath {
            private val pts = ArrayList<Pair<Float, Float>>()
            override fun moveTo(x: Float, y: Float) { pts.clear(); pts.add(x to y) }
            override fun lineTo(x: Float, y: Float) { pts.add(x to y) }
            override fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) { pts.add(x to y) }
            override fun quadTo(cx: Float, cy: Float, x: Float, y: Float) { pts.add(x to y) }
            override fun close() {}
        }
        path.moveTo(cx + rx, cy)
        for (i in 1..complexity) {
            val a = i * 2f * PI.toFloat() / complexity
            val noise = NoiseField.fbm(seed, (i / complexity.toFloat()) * 3f, 0f, 4, 2f, 0.5f)
            val r = (1f + noise * wobble)
            val x = cx + cos(a) * rx * r
            val y = cy + sin(a) * ry * r
            path.lineTo(x, y)
        }
        surface.drawPath(path, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
    }
}

object LiquidGradient : ProceduralEffect {
    override val name = "liquid_gradient"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val palette = ParamSupport.list(params, "palette", listOf("#FF6B6B", "#FFE66D", "#4ECDC4"))
        for (i in palette.indices) {
            val t = i.toFloat() / palette.size.coerceAtLeast(1)
            val y = bounds.y + bounds.height * t
            surface.setOpacity(1f / palette.size.coerceAtLeast(1))
            surface.drawRect(
                com.adnanfoisal.infinitydesign.design.dsl.Bounds(bounds.x, y, bounds.width, bounds.height / palette.size.coerceAtLeast(1) + 1f),
                com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(palette[i]),
                null, 0f
            )
        }
        surface.setOpacity(1f)
    }
}

object InkSplash : ProceduralEffect {
    override val name = "ink_splash"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#000000")
        val droplets = ParamSupport.int(params, "droplets", 30)
        val r = DeterministicRandom(seed)
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
        for (i in 0 until droplets) {
            val x = bounds.x + r.nextFloat() * bounds.width
            val y = bounds.y + r.nextFloat() * bounds.height
            val radius = r.nextFloat(2f, bounds.width.coerceAtMost(bounds.height) * 0.1f)
            surface.drawEllipse(x, y, radius, radius,
                com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
    }
}

object BlobField : ProceduralEffect {
    override val name = "blob_field"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val palette = ParamSupport.list(params, "palette", listOf("#3F51B5", "#7986CB"))
        val count = ParamSupport.int(params, "count", 8)
        val r = DeterministicRandom(seed)
        for (i in 0 until count) {
            val cx = bounds.x + r.nextFloat(0.1f, 0.9f) * bounds.width
            val cy = bounds.y + r.nextFloat(0.1f, 0.9f) * bounds.height
            val rad = r.nextFloat(0.05f, 0.3f) * bounds.width.coerceAtMost(bounds.height)
            val c = palette[r.nextInt(palette.size)]
            FluidBlob.render(seed + i * 7L,
                com.adnanfoisal.infinitydesign.design.dsl.Bounds(cx - rad, cy - rad, rad * 2f, rad * 2f),
                mapOf("color" to JsonPrimitive(c), "complexity" to JsonPrimitive(12), "wobble" to JsonPrimitive(0.3f)),
                surface)
        }
    }
}

// ====== Texture effects ======

object Grain : ProceduralEffect {
    override val name = "grain"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val intensity = ParamSupport.float(params, "intensity", 0.1f)
        val color = ParamSupport.color(params, "color", "#000000")
        val count = ParamSupport.int(params, "count", 400)
        val r = DeterministicRandom(seed)
        surface.setOpacity(SafeMath.clampSafe(intensity, 0f, 1f))
        for (i in 0 until count) {
            val x = bounds.x + r.nextFloat() * bounds.width
            val y = bounds.y + r.nextFloat() * bounds.height
            surface.drawEllipse(x, y, 1f, 1f,
                com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
        surface.setOpacity(1f)
    }
}

object PaperTexture : ProceduralEffect {
    override val name = "paper"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val base = ParamSupport.color(params, "color", "#F5F1E8")
        surface.drawRect(bounds, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(base), null, 0f)
        Grain.render(seed, bounds, mapOf("color" to JsonPrimitive("#000000"), "intensity" to JsonPrimitive(0.05f), "count" to JsonPrimitive(200)), surface)
    }
}

object Halftone : ProceduralEffect {
    override val name = "halftone"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#000000")
        val dotSize = ParamSupport.float(params, "dotSize", 6f).coerceIn(1f, 50f)
        val spacing = ParamSupport.float(params, "spacing", 14f).coerceIn(4f, 100f)
        val cols = (bounds.width / spacing).toInt().coerceIn(1, 200)
        val rows = (bounds.height / spacing).toInt().coerceIn(1, 200)
        val r = DeterministicRandom(seed)
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val x = bounds.x + i * spacing + spacing / 2f
                val y = bounds.y + j * spacing + spacing / 2f
                // Size based on noise — halftone creates a gradient feel
                val n = (NoiseField.fbm(seed, i / cols.toFloat() * 3f, j / rows.toFloat() * 3f, 3, 2f, 0.5f) + 1f) * 0.5f
                val sz = dotSize * n.coerceIn(0.1f, 1f)
                if (sz > 0.5f) surface.drawEllipse(x, y, sz, sz, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
            }
        }
    }
}

object Speckle : ProceduralEffect {
    override val name = "speckle"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val palette = ParamSupport.list(params, "palette", listOf("#FFFFFF", "#000000"))
        val count = ParamSupport.int(params, "count", 200)
        val r = DeterministicRandom(seed)
        for (i in 0 until count) {
            val x = bounds.x + r.nextFloat() * bounds.width
            val y = bounds.y + r.nextFloat() * bounds.height
            val c = palette[r.nextInt(palette.size)]
            surface.drawEllipse(x, y, 1f, 1f, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(c), null)
        }
    }
}

// ====== Technical effects ======

object Grid : ProceduralEffect {
    override val name = "grid"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#3F51B5")
        val spacing = ParamSupport.float(params, "spacing", 40f).coerceIn(4f, 1000f)
        val lineWidth = ParamSupport.float(params, "lineWidth", 1f).coerceIn(0.5f, 10f)
        val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, lineWidth)
        var x = bounds.x
        while (x <= bounds.x + bounds.width) {
            surface.drawLine(x, bounds.y, x, bounds.y + bounds.height, stroke)
            x += spacing
        }
        var y = bounds.y
        while (y <= bounds.y + bounds.height) {
            surface.drawLine(bounds.x, y, bounds.x + bounds.width, y, stroke)
            y += spacing
        }
    }
}

object Circuit : ProceduralEffect {
    override val name = "circuit"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#00E5FF")
        val lineWidth = ParamSupport.float(params, "lineWidth", 1.5f)
        val nodes = ParamSupport.int(params, "nodes", 20)
        val r = DeterministicRandom(seed)
        val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, lineWidth)
        val pts = ArrayList<Pair<Float, Float>>()
        for (i in 0 until nodes) {
            pts.add(bounds.x + r.nextFloat() * bounds.width to bounds.y + r.nextFloat() * bounds.height)
        }
        for (i in 1 until pts.size) {
            val (x1, y1) = pts[i - 1]
            val (x2, y2) = pts[i]
            // L-shape — Manhattan routing for circuit feel
            surface.drawLine(x1, y1, x2, y1, stroke)
            surface.drawLine(x2, y1, x2, y2, stroke)
            // pad
            surface.drawEllipse(x2, y2, 2f, 2f, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
    }
}

object TechnicalLines : ProceduralEffect {
    override val name = "technical_lines"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#3F51B5")
        val count = ParamSupport.int(params, "count", 30)
        val lineWidth = ParamSupport.float(params, "lineWidth", 1f)
        val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, lineWidth)
        val r = DeterministicRandom(seed)
        for (i in 0 until count) {
            val x1 = bounds.x + r.nextFloat() * bounds.width
            val y1 = bounds.y + r.nextFloat() * bounds.height
            val len = r.nextFloat(0.05f, 0.5f) * bounds.width.coerceAtMost(bounds.height)
            val angle = r.nextFloat() * 2f * PI.toFloat()
            val x2 = x1 + cos(angle) * len
            val y2 = y1 + sin(angle) * len
            surface.drawLine(x1, y1, x2, y2, stroke)
        }
    }
}

object BlueprintLines : ProceduralEffect {
    override val name = "blueprint_lines"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        Grid.render(seed, bounds, mapOf("color" to JsonPrimitive("#00E5FF"), "spacing" to JsonPrimitive(40f), "lineWidth" to JsonPrimitive(0.5f)), surface)
        Grid.render(seed xor 1L, bounds, mapOf("color" to JsonPrimitive("#00E5FF"), "spacing" to JsonPrimitive(200f), "lineWidth" to JsonPrimitive(1.5f)), surface)
    }
}

object Rings : ProceduralEffect {
    override val name = "rings"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#3F51B5")
        val count = ParamSupport.int(params, "count", 6)
        val r = DeterministicRandom(seed)
        val cx = bounds.x + bounds.width * 0.5f
        val cy = bounds.y + bounds.height * 0.5f
        val maxR = bounds.width.coerceAtMost(bounds.height) * 0.5f
        for (i in 1..count) {
            val rad = maxR * (i.toFloat() / count)
            val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, 1.5f)
            surface.drawEllipse(cx, cy, rad, rad, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.None, stroke)
        }
    }
}

// ====== Abstract effects ======

object Rays : ProceduralEffect {
    override val name = "rays"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#FFE082")
        val count = ParamSupport.int(params, "count", 12)
        val cx = bounds.x + bounds.width * 0.5f
        val cy = bounds.y + bounds.height * 0.5f
        val maxR = bounds.width.coerceAtMost(bounds.height) * 0.6f
        val r = DeterministicRandom(seed)
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.SCREEN)
        for (i in 0 until count) {
            val a = i * 2f * PI.toFloat() / count
            val x2 = cx + cos(a) * maxR
            val y2 = cy + sin(a) * maxR
            val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, 2f)
            surface.drawLine(cx, cy, x2, y2, stroke)
        }
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
    }
}

object WaveField : ProceduralEffect {
    override val name = "wave_field"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#3F51B5")
        val count = ParamSupport.int(params, "count", 5)
        val amp = ParamSupport.float(params, "amplitude", 30f)
        val freq = ParamSupport.float(params, "frequency", 1f)
        for (j in 0 until count) {
            val yBase = bounds.y + bounds.height * (j + 1) / (count + 1)
            val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, 1.5f)
            // sample 80 points and approximate with line segments
            val pts = (0..80).map { i ->
                val t = i / 80f
                val x = bounds.x + t * bounds.width
                val y = yBase + sin(t * 2f * PI.toFloat() * freq + j) * amp
                x to y
            }
            for (i in 1 until pts.size) {
                surface.drawLine(pts[i-1].first, pts[i-1].second, pts[i].first, pts[i].second, stroke)
            }
        }
    }
}

object ParticleField : ProceduralEffect {
    override val name = "particle_field"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#FFFFFF")
        val count = ParamSupport.int(params, "count", 100)
        val r = DeterministicRandom(seed)
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.SCREEN)
        for (i in 0 until count) {
            val x = bounds.x + r.nextFloat() * bounds.width
            val y = bounds.y + r.nextFloat() * bounds.height
            val sz = r.nextFloat(0.5f, 3f)
            surface.drawEllipse(x, y, sz, sz, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
    }
}

object OrbitSystem : ProceduralEffect {
    override val name = "orbit_system"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#FFE082")
        val count = ParamSupport.int(params, "orbits", 4)
        val r = DeterministicRandom(seed)
        val cx = bounds.x + bounds.width * 0.5f
        val cy = bounds.y + bounds.height * 0.5f
        val maxR = bounds.width.coerceAtMost(bounds.height) * 0.5f
        for (i in 1..count) {
            val rad = maxR * (i.toFloat() / count)
            surface.drawEllipse(cx, cy, rad, rad, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.None,
                com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, 1f))
            // planet
            val a = r.nextFloat() * 2f * PI.toFloat()
            val px = cx + cos(a) * rad
            val py = cy + sin(a) * rad
            surface.drawEllipse(px, py, 4f, 4f, com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
    }
}

object Glow : ProceduralEffect {
    override val name = "glow"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#FFFFFF")
        val intensity = ParamSupport.float(params, "intensity", 0.6f)
        val radius = bounds.width.coerceAtMost(bounds.height) * ParamSupport.float(params, "radius", 0.5f)
        val cx = bounds.x + bounds.width / 2f
        val cy = bounds.y + bounds.height / 2f
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.SCREEN)
        for (i in 8 downTo 1) {
            val f = i / 8f
            surface.setOpacity(SafeMath.clampSafe(intensity * (1f - f), 0f, 1f))
            surface.drawEllipse(cx, cy, radius * f, radius * f,
                com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color), null)
        }
        surface.setBlendMode(com.adnanfoisal.infinitydesign.graphics.renderer.BlendMode.NORMAL)
        surface.setOpacity(1f)
    }
}

object VolumetricGlow : ProceduralEffect {
    override val name = "volumetric_glow"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        Glow.render(seed, bounds, params + ("intensity" to JsonPrimitive(0.8f)), surface)
        Rays.render(seed + 1L, bounds, params + ("count" to JsonPrimitive(24)), surface)
    }
}

object MeshTexture : ProceduralEffect {
    override val name = "mesh_texture"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val palette = ParamSupport.list(params, "palette", listOf("#3F51B5", "#7986CB", "#FFE082"))
        val cells = ParamSupport.int(params, "cells", 8)
        val cellW = bounds.width / cells
        val cellH = bounds.height / cells
        val r = DeterministicRandom(seed)
        for (j in 0 until cells) {
            for (i in 0 until cells) {
                val color = palette[r.nextInt(palette.size)]
                val x = bounds.x + i * cellW
                val y = bounds.y + j * cellH
                val noise = (NoiseField.gradient(seed, i.toFloat(), j.toFloat()) + 1f) * 0.5f
                surface.setOpacity(0.3f + noise * 0.4f)
                surface.drawRect(
                    com.adnanfoisal.infinitydesign.design.dsl.Bounds(x, y, cellW + 1, cellH + 1),
                    com.adnanfoisal.infinitydesign.design.dsl.FillSpec.Solid(color),
                    null, 0f)
            }
        }
        surface.setOpacity(1f)
    }
}

object DistortionField : ProceduralEffect {
    override val name = "distortion_field"
    override fun render(seed: Long, bounds: Bounds, params: Map<String, JsonElement>, surface: DrawSurface) {
        val color = ParamSupport.color(params, "color", "#3F51B5")
        val count = ParamSupport.int(params, "count", 20)
        val r = DeterministicRandom(seed)
        val stroke = com.adnanfoisal.infinitydesign.design.dsl.StrokeSpec(color, 1.5f)
        for (i in 0 until count) {
            val x = bounds.x + r.nextFloat() * bounds.width
            val y = bounds.y + r.nextFloat() * bounds.height
            val len = r.nextFloat(0.05f, 0.2f) * bounds.width
            val a = r.nextFloat() * 2f * PI.toFloat()
            val x2 = x + cos(a) * len
            val y2 = y + sin(a) * len
            // wavy segment
            val midX = (x + x2) / 2f + (r.nextFloat() - 0.5f) * 20f
            val midY = (y + y2) / 2f + (r.nextFloat() - 0.5f) * 20f
            surface.drawLine(x, y, midX, midY, stroke)
            surface.drawLine(midX, midY, x2, y2, stroke)
        }
    }
}
