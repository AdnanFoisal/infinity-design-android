package com.adnanfoisal.infinitydesign.design.dsl

import com.adnanfoisal.infinitydesign.core.util.SafeMath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sealed hierarchy of design elements. Each element is parametric — the renderer
 * reads these and turns them into Skia drawing operations.
 *
 * Section 14: do not rasterize everything. Text stays text, shapes stay shapes,
 * procedural effects stay procedural.
 */
@Serializable
sealed class DesignElement {
    abstract val id: String
    abstract val bounds: Bounds
    abstract val rotation: Float
    abstract val opacity: Float
    abstract val visible: Boolean
    abstract val locked: Boolean
    abstract val name: String

    @Serializable
    @SerialName("text")
    data class Text(
        @SerialName("id") override val id: String,
        @SerialName("bounds") override val bounds: Bounds,
        @SerialName("rotation") override val rotation: Float = 0f,
        @SerialName("opacity") override val opacity: Float = 1f,
        @SerialName("visible") override val visible: Boolean = true,
        @SerialName("locked") override val locked: Boolean = false,
        @SerialName("name") override val name: String = "Text",
        @SerialName("content") val content: String,
        @SerialName("fontRole") val fontRole: String = "body",
        @SerialName("fontSize") val fontSize: Float = 16f,
        @SerialName("color") val color: String = "#000000",
        @SerialName("alignment") val alignment: TextAlignment = TextAlignment.LEFT,
        @SerialName("lineSpacing") val lineSpacing: Float = 1.2f,
        @SerialName("letterSpacing") val letterSpacing: Float = 0f,
        @SerialName("weight") val weight: Int = 400,
        @SerialName("italic") val italic: Boolean = false,
        @SerialName("uppercase") val uppercase: Boolean = false,
        @SerialName("truncate") val truncate: Boolean = false,
    ) : DesignElement() {
        override fun withBounds(b: Bounds) = copy(bounds = b)
        override fun withOpacity(o: Float) = copy(opacity = o)
        override fun withVisible(v: Boolean) = copy(visible = v)
        override fun withLocked(l: Boolean) = copy(locked = l)
        override fun withRotation(r: Float) = copy(rotation = r)
    }

    @Serializable
    @SerialName("shape")
    data class Shape(
        @SerialName("id") override val id: String,
        @SerialName("bounds") override val bounds: Bounds,
        @SerialName("rotation") override val rotation: Float = 0f,
        @SerialName("opacity") override val opacity: Float = 1f,
        @SerialName("visible") override val visible: Boolean = true,
        @SerialName("locked") override val locked: Boolean = false,
        @SerialName("name") override val name: String = "Shape",
        @SerialName("kind") val kind: ShapeKind,
        @SerialName("fill") val fill: FillSpec = FillSpec.None,
        @SerialName("stroke") val stroke: StrokeSpec? = null,
        @SerialName("cornerRadius") val cornerRadius: Float = 0f,
        @SerialName("cornerSmoothing") val cornerSmoothing: Float = 0f,
    ) : DesignElement() {
        override fun withBounds(b: Bounds) = copy(bounds = b)
        override fun withOpacity(o: Float) = copy(opacity = o)
        override fun withVisible(v: Boolean) = copy(visible = v)
        override fun withLocked(l: Boolean) = copy(locked = l)
        override fun withRotation(r: Float) = copy(rotation = r)
    }

    @Serializable
    @SerialName("procedural")
    data class Procedural(
        @SerialName("id") override val id: String,
        @SerialName("bounds") override val bounds: Bounds,
        @SerialName("rotation") override val rotation: Float = 0f,
        @SerialName("opacity") override val opacity: Float = 1f,
        @SerialName("visible") override val visible: Boolean = true,
        @SerialName("locked") override val locked: Boolean = false,
        @SerialName("name") override val name: String = "Procedural",
        @SerialName("effect") val effect: String,
        @SerialName("params") val params: Map<String, JsonElement> = emptyMap(),
        @SerialName("seed") val seed: Long = 0L,
        @SerialName("blendMode") val blendMode: String = "normal",
    ) : DesignElement() {
        override fun withBounds(b: Bounds) = copy(bounds = b)
        override fun withOpacity(o: Float) = copy(opacity = o)
        override fun withVisible(v: Boolean) = copy(visible = v)
        override fun withLocked(l: Boolean) = copy(locked = l)
        override fun withRotation(r: Float) = copy(rotation = r)
    }

    @Serializable
    @SerialName("image")
    data class Image(
        @SerialName("id") override val id: String,
        @SerialName("bounds") override val bounds: Bounds,
        @SerialName("rotation") override val rotation: Float = 0f,
        @SerialName("opacity") override val opacity: Float = 1f,
        @SerialName("visible") override val visible: Boolean = true,
        @SerialName("locked") override val locked: Boolean = false,
        @SerialName("name") override val name: String = "Image",
        @SerialName("assetId") val assetId: String,
        @SerialName("fit") val fit: ImageFit = ImageFit.COVER,
        @SerialName("cornerRadius") val cornerRadius: Float = 0f,
    ) : DesignElement() {
        override fun withBounds(b: Bounds) = copy(bounds = b)
        override fun withOpacity(o: Float) = copy(opacity = o)
        override fun withVisible(v: Boolean) = copy(visible = v)
        override fun withLocked(l: Boolean) = copy(locked = l)
        override fun withRotation(r: Float) = copy(rotation = r)
    }

    @Serializable
    @SerialName("group")
    data class Group(
        @SerialName("id") override val id: String,
        @SerialName("bounds") override val bounds: Bounds,
        @SerialName("rotation") override val rotation: Float = 0f,
        @SerialName("opacity") override val opacity: Float = 1f,
        @SerialName("visible") override val visible: Boolean = true,
        @SerialName("locked") override val locked: Boolean = false,
        @SerialName("name") override val name: String = "Group",
        @SerialName("childrenIds") val childrenIds: List<String> = emptyList(),
    ) : DesignElement() {
        override fun withBounds(b: Bounds) = copy(bounds = b)
        override fun withOpacity(o: Float) = copy(opacity = o)
        override fun withVisible(v: Boolean) = copy(visible = v)
        override fun withLocked(l: Boolean) = copy(locked = l)
        override fun withRotation(r: Float) = copy(rotation = r)
    }

    abstract fun withBounds(b: Bounds): DesignElement
    abstract fun withOpacity(o: Float): DesignElement
    abstract fun withVisible(v: Boolean): DesignElement
    abstract fun withLocked(l: Boolean): DesignElement
    abstract fun withRotation(r: Float): DesignElement

    fun validate(): Boolean {
        return SafeMath.allFinite(
            bounds.x, bounds.y, bounds.width, bounds.height,
            rotation, opacity,
        ) && opacity in 0f..1f && bounds.width >= 0f && bounds.height >= 0f
    }
}

@Serializable
enum class TextAlignment { LEFT, CENTER, RIGHT, JUSTIFY }

@Serializable
enum class ShapeKind {
    RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, TRIANGLE, LINE, POLYGON, STAR, CUSTOM_PATH
}

@Serializable
enum class ImageFit { FILL, COVER, CONTAIN, TILE }

@Serializable
sealed class FillSpec {
    @Serializable
    @SerialName("none")
    data object None : FillSpec()

    @Serializable
    @SerialName("solid")
    data class Solid(val color: String) : FillSpec()

    @Serializable
    @SerialName("linear")
    data class Linear(val stops: List<ColorStop>, val angle: Float = 0f) : FillSpec()

    @Serializable
    @SerialName("radial")
    data class Radial(
        val stops: List<ColorStop>,
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val radius: Float = 0.7f,
    ) : FillSpec()
}

@Serializable
data class StrokeSpec(
    val color: String,
    val width: Float = 1f,
    val dashPattern: List<Float> = emptyList(),
    val cap: String = "butt",
    val join: String = "miter",
)
