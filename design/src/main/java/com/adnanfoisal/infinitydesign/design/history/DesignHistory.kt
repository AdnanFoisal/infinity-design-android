package com.adnanfoisal.infinitydesign.design.history

import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.design.commands.DesignCommand
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Undo/redo history bound to a single open design document.
 *
 * Coalescing: when the user starts a continuous gesture (drag/resize/rotate),
 * the caller wraps the gesture in [beginCoalesce]/[endCoalesce]. Commands pushed
 * between those calls share a coalesce key and are merged into one undo entry.
 *
 * Section 29: continuous gestures must not produce hundreds of undo entries.
 */
class DesignHistory(
    initial: DesignDocument,
    private val maxStack: Int = 256,
) {
    private val undoStack: ArrayDeque<DesignCommand> = ArrayDeque()
    private val redoStack: ArrayDeque<DesignCommand> = ArrayDeque()
    private var coalescingKey: String? = null
    private val coalescedBuffer: ArrayDeque<DesignCommand> = ArrayDeque()

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<DesignDocument> = _state.asStateFlow()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun push(cmd: DesignCommand): AppResult<DesignDocument> {
        val key = cmd.coalesceKey()
        // Coalesce: if same key as the active coalescing session, merge.
        if (key != null && key == coalescingKey) {
            coalescedBuffer.addLast(cmd)
            val next = applyInternal(cmd) ?: return okResult(_state.value)
            _state.value = next
            return okResult(_state.value)
        }
        // Otherwise flush any open coalesce buffer first, then push directly.
        flushCoalesce()
        when (val r = cmd.apply(_state.value)) {
            is AppResult.Ok -> {
                _state.value = r.value
                pushUndo(cmd)
                redoStack.clear()
                trim()
            }
            is AppResult.Err -> return r
        }
        return okResult(_state.value)
    }

    fun beginCoalesce(key: String) {
        coalescingKey = key
        coalescedBuffer.clear()
    }

    fun endCoalesce() {
        if (coalescedBuffer.isNotEmpty()) {
            val composite = DesignCommand.Coalesced(
                id = "coalesce-${coalescedBuffer.first().id}",
                targetId = coalescedBuffer.first().targetId,
                commands = coalescedBuffer.toList(),
            )
            pushUndo(composite)
        }
        coalescingKey = null
        coalescedBuffer.clear()
    }

    private fun flushCoalesce() {
        if (coalescingKey != null) {
            endCoalesce()
        }
    }

    private fun pushUndo(cmd: DesignCommand) {
        undoStack.addLast(cmd)
        while (undoStack.size > maxStack) undoStack.removeFirst()
    }

    fun undo(): AppResult<DesignDocument> {
        flushCoalesce()
        val cmd = undoStack.removeLastOrNull()
            ?: return okResult(_state.value)
        when (val r = cmd.inverse(_state.value)) {
            is AppResult.Ok -> {
                _state.value = r.value
                redoStack.addLast(cmd)
            }
            is AppResult.Err -> {
                undoStack.addLast(cmd)
                return r
            }
        }
        return okResult(_state.value)
    }

    fun redo(): AppResult<DesignDocument> {
        flushCoalesce()
        val cmd = redoStack.removeLastOrNull()
            ?: return okResult(_state.value)
        when (val r = cmd.apply(_state.value)) {
            is AppResult.Ok -> {
                _state.value = r.value
                undoStack.addLast(cmd)
            }
            is AppResult.Err -> {
                redoStack.addLast(cmd)
                return r
            }
        }
        return okResult(_state.value)
    }

    fun replaceCurrent(doc: DesignDocument) {
        _state.value = doc
        undoStack.clear()
        redoStack.clear()
    }

    private fun trim() {
        while (undoStack.size > maxStack) undoStack.removeFirst()
    }

    private fun applyInternal(cmd: DesignCommand): DesignDocument? {
        // We trust the caller here — coalesce buffer path is internal.
        return (cmd.apply(_state.value) as? AppResult.Ok)?.value
    }
}
