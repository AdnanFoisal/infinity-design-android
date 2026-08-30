package com.adnanfoisal.infinitydesign.design.commands

import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement

/**
 * Command pattern for design mutations.
 *
 * Section 29: undo/redo must be command-based, not whole-UI snapshots.
 * Section 28: locked elements must be protected by the mutation system, not just the UI.
 * Section 29: continuous drags must coalesce so a 2-second drag doesn't produce
 * hundreds of undo entries.
 */
sealed class DesignCommand {

    abstract val id: String
    abstract val targetId: String
    abstract fun apply(doc: DesignDocument): AppResult<DesignDocument>
    abstract fun inverse(doc: DesignDocument): AppResult<DesignDocument>

    /** Coalescing key — commands with the same key during a continuous gesture
     * are merged into one undo step. */
    open fun coalesceKey(): String? = null

    data class MoveElement(
        override val id: String,
        override val targetId: String,
        val dx: Float,
        val dy: Float,
    ) : DesignCommand() {
        override fun coalesceKey() = "move:$targetId"
        override fun apply(doc: DesignDocument): AppResult<DesignDocument> = updateElement(doc, targetId) { el ->
            if (el.locked) return errLocked(targetId)
            okResult(el.withBounds(Bounds(
                el.bounds.x + dx,
                el.bounds.y + dy,
                el.bounds.width,
                el.bounds.height,
            )))
        }
        override fun inverse(doc: DesignDocument): AppResult<DesignDocument> = updateElement(doc, targetId) { el ->
            okResult(el.withBounds(Bounds(
                el.bounds.x - dx, el.bounds.y - dy,
                el.bounds.width, el.bounds.height,
            )))
        }
    }

    data class ResizeElement(
        override val id: String,
        override val targetId: String,
        val newBounds: Bounds,
        val oldBounds: Bounds,
    ) : DesignCommand() {
        override fun coalesceKey() = "resize:$targetId"
        override fun apply(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            if (el.locked) return errLocked(targetId)
            if (!SafeMath.allFinite(newBounds.x, newBounds.y, newBounds.width, newBounds.height))
                return errResult(AppError.Kind.InvalidCoordinate, "non-finite resize")
            if (newBounds.width < 0f || newBounds.height < 0f)
                return errResult(AppError.Kind.InvalidDimension, "negative resize")
            okResult(el.withBounds(newBounds))
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withBounds(oldBounds))
        }
    }

    data class RotateElement(
        override val id: String,
        override val targetId: String,
        val delta: Float,
    ) : DesignCommand() {
        override fun coalesceKey() = "rotate:$targetId"
        override fun apply(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            if (el.locked) return errLocked(targetId)
            okResult(el.withRotation(el.rotation + delta))
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withRotation(el.rotation - delta))
        }
    }

    data class ChangeOpacity(
        override val id: String,
        override val targetId: String,
        val newOpacity: Float,
        val oldOpacity: Float,
    ) : DesignCommand() {
        override fun coalesceKey() = "opacity:$targetId"
        override fun apply(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            if (el.locked) return errLocked(targetId)
            val o = SafeMath.clampSafe(newOpacity, 0f, 1f)
            okResult(el.withOpacity(o))
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withOpacity(oldOpacity))
        }
    }

    data class ToggleVisibility(
        override val id: String,
        override val targetId: String,
    ) : DesignCommand() {
        override fun apply(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withVisible(!el.visible))
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withVisible(!el.visible))
        }
    }

    data class ToggleLock(
        override val id: String,
        override val targetId: String,
    ) : DesignCommand() {
        override fun apply(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withLocked(!el.locked))
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            okResult(el.withLocked(!el.locked))
        }
    }

    data class AddElement(
        override val id: String,
        override val targetId: String,
        val element: DesignElement,
    ) : DesignCommand() {
        override fun apply(doc: DesignDocument) = okResult(doc.copy(elements = doc.elements + element))
        override fun inverse(doc: DesignDocument) = okResult(doc.copy(elements = doc.elements.filterNot { it.id == targetId }))
    }

    data class DeleteElement(
        override val id: String,
        override val targetId: String,
    ) : DesignCommand() {
        override fun apply(doc: DesignDocument): AppResult<DesignDocument> {
            val el = doc.elements.find { it.id == targetId }
                ?: return errResult(AppError.Kind.NotFound, "element $targetId")
            if (el.locked) return errLocked(targetId)
            return okResult(doc.copy(elements = doc.elements.filterNot { it.id == targetId }))
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) { el ->
            // re-add if not already present
            okResult(el)
        }
    }

    data class ReplaceElement(
        override val id: String,
        override val targetId: String,
        val newElement: DesignElement,
        val oldElement: DesignElement,
    ) : DesignCommand() {
        override fun apply(doc: DesignDocument) = updateElement(doc, targetId) {
            if (it.locked) errLocked(targetId) else okResult(newElement)
        }
        override fun inverse(doc: DesignDocument) = updateElement(doc, targetId) {
            okResult(oldElement)
        }
    }

    /**
     * Coalesced composite — used to merge a sequence of coalesceable commands
     * (a continuous drag) into a single undo step.
     */
    data class Coalesced(
        override val id: String,
        override val targetId: String,
        val commands: List<DesignCommand>,
    ) : DesignCommand() {
        init { require(commands.isNotEmpty()) }
        override fun apply(doc: DesignDocument): AppResult<DesignDocument> {
            var current = doc
            for (c in commands) {
                when (val r = c.apply(current)) {
                    is AppResult.Ok -> current = r.value
                    is AppResult.Err -> return r
                }
            }
            return okResult(current)
        }
        override fun inverse(doc: DesignDocument): AppResult<DesignDocument> {
            var current = doc
            for (c in commands.reversed()) {
                when (val r = c.inverse(current)) {
                    is AppResult.Ok -> current = r.value
                    is AppResult.Err -> return r
                }
            }
            return okResult(current)
        }
    }
}

private fun errLocked(targetId: String): AppResult<Nothing> =
    errResult(AppError.Kind.LockedElement, "Element $targetId is locked")

private inline fun updateElement(
    doc: DesignDocument,
    targetId: String,
    transform: (DesignElement) -> AppResult<DesignElement>,
): AppResult<DesignDocument> {
    val idx = doc.elements.indexOfFirst { it.id == targetId }
    if (idx < 0) return errResult(AppError.Kind.NotFound, "Element $targetId not found")
    val current = doc.elements[idx]
    return when (val r = transform(current)) {
        is AppResult.Ok -> {
            val newElements = doc.elements.toMutableList()
            newElements[idx] = r.value
            okResult(doc.copy(elements = newElements))
        }
        is AppResult.Err -> r
    }
}
