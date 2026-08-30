package com.adnanfoisal.infinitydesign.design.layout

import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.AnchorTarget
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.LayoutConstraint
import com.adnanfoisal.infinitydesign.design.typography.MeasuredText
import com.adnanfoisal.infinitydesign.design.typography.TypographyEngine

/**
 * Resolves [LayoutConstraint]s into absolute [Bounds]. Section 15 of the spec.
 *
 * The engine operates in two passes:
 *   1. Measure text elements to learn their natural width/height.
 *   2. Apply anchor constraints, computing final positions from siblings + parent.
 *
 * Elements without explicit constraints keep their authored bounds.
 *
 * Section 48: full bounds checking — elements that fall outside the canvas are
 * flagged for the candidate scorer to penalise.
 */
class LayoutEngine(private val typography: TypographyEngine) {

    data class LayoutResult(
        val document: DesignDocument,
        val measured: Map<String, MeasuredText>,
        val outOfBounds: List<String>,
    )

    fun resolve(doc: DesignDocument): LayoutResult {
        if (!SafeMath.allFinite(doc.canvas.width, doc.canvas.height)) {
            return LayoutResult(doc, emptyMap(), emptyList())
        }
        // Pass 1 — measure text elements
        val measured = HashMap<String, MeasuredText>()
        for (el in doc.elements) {
            if (el is DesignElement.Text) {
                val maxWidth = if (el.bounds.width > 0f) el.bounds.width else doc.canvas.width * 0.8f
                val m = typography.measure(
                    text = if (el.uppercase) el.content.uppercase() else el.content,
                    fontRole = el.fontRole,
                    fontSize = el.fontSize,
                    maxWidth = maxWidth,
                    alignment = el.alignment,
                    lineSpacing = el.lineSpacing,
                    weight = el.weight,
                    italic = el.italic,
                    letterSpacing = el.letterSpacing,
                    truncate = el.truncate,
                )
                measured[el.id] = m
            }
        }
        // Pass 2 — apply constraints
        var elements = doc.elements
        val constraints = doc.constraints.groupBy { elementIdOf(it) }
        for (el in elements.toList()) {
            val list = constraints[el.id] ?: continue
            var newEl = el
            for (c in list) {
                newEl = when (c) {
                    is LayoutConstraint.Anchor -> applyAnchor(newEl, c, elements, doc.canvas)
                    is LayoutConstraint.MaxWidth -> {
                        val v = if (c.unit == com.adnanfoisal.infinitydesign.design.dsl.ConstraintUnit.FRACTION)
                            c.value * doc.canvas.width else c.value
                        val b = newEl.bounds
                        newEl.withBounds(Bounds(b.x, b.y, v.coerceIn(1f, doc.canvas.width), b.height))
                    }
                    is LayoutConstraint.MaxHeight -> {
                        val v = if (c.unit == com.adnanfoisal.infinitydesign.design.dsl.ConstraintUnit.FRACTION)
                            c.value * doc.canvas.height else c.value
                        val b = newEl.bounds
                        newEl.withBounds(Bounds(b.x, b.y, b.width, v.coerceIn(1f, doc.canvas.height)))
                    }
                    is LayoutConstraint.AspectRatio -> {
                        val b = newEl.bounds
                        val ratio = SafeMath.sanitize(c.ratio, 1f).coerceIn(0.05f, 50f)
                        val h = b.width / ratio
                        newEl.withBounds(Bounds(b.x, b.y, b.width, h))
                    }
                    is LayoutConstraint.Spacing -> applySpacing(newEl, c, elements)
                    is LayoutConstraint.SafeZone -> newEl
                }
            }
            if (newEl !== el) {
                elements = elements.map { if (it.id == newEl.id) newEl else it }
            }
        }
        // Pass 3 — out-of-bounds detection
        val outOfBounds = elements.filter { el ->
            val b = el.bounds
            b.x < 0f || b.y < 0f ||
                b.x + b.width > doc.canvas.width ||
                b.y + b.height > doc.canvas.height
        }.map { it.id }

        return LayoutResult(doc.copy(elements = elements), measured, outOfBounds)
    }

    private fun elementIdOf(c: LayoutConstraint): String = when (c) {
        is LayoutConstraint.Anchor -> c.elementId
        is LayoutConstraint.MaxWidth -> c.elementId
        is LayoutConstraint.MaxHeight -> c.elementId
        is LayoutConstraint.AspectRatio -> c.elementId
        is LayoutConstraint.Spacing -> c.elementId
        is LayoutConstraint.SafeZone -> ""
    }

    private fun applyAnchor(el: DesignElement, c: LayoutConstraint.Anchor, all: List<DesignElement>, canvas: CanvasSpec): DesignElement {
        val b = el.bounds
        val parent = if (c.parent) canvas.width to canvas.height else {
            val sib = all.find { it.id == c.siblingId } ?: return el
            sib.bounds.x + sib.bounds.width to (sib.bounds.y + sib.bounds.height)
        }
        val (parentX, parentY) = if (c.parent) 0f to 0f else {
            val sib = all.find { it.id == c.siblingId } ?: return el
            sib.bounds.x to sib.bounds.y
        }
        val parentW = parent.first
        val parentH = parent.second
        val (nx, ny) = when (c.target) {
            AnchorTarget.LEFT, AnchorTarget.PARENT_LEFT -> parentX to b.y
            AnchorTarget.RIGHT, AnchorTarget.PARENT_RIGHT -> (parentX + parentW - b.width) to b.y
            AnchorTarget.TOP, AnchorTarget.PARENT_TOP -> b.x to parentY
            AnchorTarget.BOTTOM, AnchorTarget.PARENT_BOTTOM -> b.x to (parentY + parentH - b.height)
            AnchorTarget.CENTER_X, AnchorTarget.PARENT_CENTER_X -> (parentX + (parentW - b.width) / 2f) to b.y
            AnchorTarget.CENTER_Y, AnchorTarget.PARENT_CENTER_Y -> b.x to (parentY + (parentH - b.height) / 2f)
        }
        val offsetX = c.offset
        val offsetY = c.offset
        val finalX = SafeMath.sanitize(nx + offsetX, b.x)
        val finalY = SafeMath.sanitize(ny + offsetY, b.y)
        return el.withBounds(Bounds(finalX, finalY, b.width, b.height))
    }

    private fun applySpacing(el: DesignElement, c: LayoutConstraint.Spacing, all: List<DesignElement>): DesignElement {
        val sib = all.find { it.id == c.siblingId } ?: return el
        val b = el.bounds
        val nx = when (c.target) {
            AnchorTarget.LEFT, AnchorTarget.PARENT_LEFT -> sib.bounds.x - b.width - c.value
            AnchorTarget.RIGHT, AnchorTarget.PARENT_RIGHT -> sib.bounds.x + sib.bounds.width + c.value
            else -> b.x
        }
        val ny = when (c.target) {
            AnchorTarget.TOP, AnchorTarget.PARENT_TOP -> sib.bounds.y - b.height - c.value
            AnchorTarget.BOTTOM, AnchorTarget.PARENT_BOTTOM -> sib.bounds.y + sib.bounds.height + c.value
            else -> b.y
        }
        return el.withBounds(Bounds(SafeMath.sanitize(nx, b.x), SafeMath.sanitize(ny, b.y), b.width, b.height))
    }
}
