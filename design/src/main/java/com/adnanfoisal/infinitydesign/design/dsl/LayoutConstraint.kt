package com.adnanfoisal.infinitydesign.design.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Layout constraints — the layout engine consumes these instead of pure x/y.
 * Section 15 of the spec.
 *
 * Anchors are expressed in fractions of the parent (0..1) for resilience against
 * text-length changes. The engine resolves them to absolute coordinates after
 * measuring text and solving the constraint system.
 */
@Serializable
sealed class LayoutConstraint {
    @Serializable
    @SerialName("anchor")
    data class Anchor(
        @SerialName("elementId") val elementId: String,
        @SerialName("target") val target: AnchorTarget,
        @SerialName("parent") val parent: Boolean = true,
        @SerialName("siblingId") val siblingId: String? = null,
        @SerialName("siblingEdge") val siblingEdge: String? = null,
        @SerialName("offset") val offset: Float = 0f,
        @SerialName("priority") val priority: Int = 100,
    ) : LayoutConstraint()

    @Serializable
    @SerialName("maxWidth")
    data class MaxWidth(
        @SerialName("elementId") val elementId: String,
        @SerialName("value") val value: Float,
        @SerialName("unit") val unit: ConstraintUnit = ConstraintUnit.FRACTION,
    ) : LayoutConstraint()

    @Serializable
    @SerialName("maxHeight")
    data class MaxHeight(
        @SerialName("elementId") val elementId: String,
        @SerialName("value") val value: Float,
        @SerialName("unit") val unit: ConstraintUnit = ConstraintUnit.FRACTION,
    ) : LayoutConstraint()

    @Serializable
    @SerialName("aspect")
    data class AspectRatio(
        @SerialName("elementId") val elementId: String,
        @SerialName("ratio") val ratio: Float,
    ) : LayoutConstraint()

    @Serializable
    @SerialName("spacing")
    data class Spacing(
        @SerialName("elementId") val elementId: String,
        @SerialName("target") val target: AnchorTarget,
        @SerialName("siblingId") val siblingId: String,
        @SerialName("value") val value: Float,
    ) : LayoutConstraint()

    @Serializable
    @SerialName("safeZone")
    data class SafeZone(
        @SerialName("top") val top: Float = 0.05f,
        @SerialName("bottom") val bottom: Float = 0.95f,
        @SerialName("left") val left: Float = 0.05f,
        @SerialName("right") val right: Float = 0.95f,
    ) : LayoutConstraint()
}

@Serializable
enum class AnchorTarget {
    LEFT, RIGHT, TOP, BOTTOM, CENTER_X, CENTER_Y,
    PARENT_LEFT, PARENT_RIGHT, PARENT_TOP, PARENT_BOTTOM, PARENT_CENTER_X, PARENT_CENTER_Y,
}

@Serializable
enum class ConstraintUnit { FRACTION, PIXEL }
