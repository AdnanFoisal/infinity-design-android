package com.adnanfoisal.infinitydesign.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanfoisal.infinitydesign.core.dispatchers.AppDispatchers
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.data.repositories.ProjectRepository
import com.adnanfoisal.infinitydesign.design.composition.Compositions
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.layout.CandidateGenerator
import com.adnanfoisal.infinitydesign.design.validation.DesignValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DirectionViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val candidateGen: CandidateGenerator,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        object NotFound : UiState()
        data class Loaded(val blueprint: DesignBlueprint) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var currentBlueprint: DesignBlueprint? = null

    fun load(blueprintId: String) {
        viewModelScope.launch(dispatchers.io) {
            // Cached blueprint in the database.
            // For simplicity we look up by ID via findCachedBlueprints — but since we
            // only cache by prompt, we cannot look up by ID directly. Use the last cache.
            // For a real implementation: store blueprint by ID with a dedicated lookup.
            val cached = repo.findCachedBlueprints("", limit = 100)
                .firstOrNull { it.id == blueprintId }
            if (cached == null) {
                _state.value = UiState.NotFound
                return@launch
            }
            currentBlueprint = cached
            _state.value = UiState.Loaded(cached)
        }
    }

    /**
     * Stage 2: compile the blueprint into a DesignDocument and save it.
     * Section 8: the engine generates candidates and picks the best (Section 17).
     */
    fun accept(onProjectSaved: (String) -> Unit) {
        val bp = currentBlueprint ?: return
        viewModelScope.launch(dispatchers.default) {
            val canvas = CanvasSpec.POSTER_PORTRAIT
            val candidates = candidateGen.generateCandidates(bp, canvas, count = 6)
            val best = candidateGen.best(candidates)?.document
                ?: candidates.firstOrNull()?.document
                ?: Compositions.apply(canvas, bp, bp.seed).let { skeleton ->
                    // Build a minimal document from the skeleton if no candidate worked.
                    DesignDocument(
                        id = java.util.UUID.randomUUID().toString(),
                        name = bp.title,
                        canvas = canvas,
                        background = com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec.Solid(bp.palette.background),
                        palette = com.adnanfoisal.infinitydesign.design.dsl.PaletteSpec(
                            primary = bp.palette.primary,
                            secondary = bp.palette.secondary,
                            accent = bp.palette.accent,
                            muted = bp.palette.neutrals,
                            onPrimary = bp.palette.foreground,
                            onSecondary = bp.palette.background,
                        ),
                        typography = com.adnanfoisal.infinitydesign.design.dsl.TypographySpec(
                            display = bp.typography.displayRole,
                            body = bp.typography.bodyRole,
                            caption = bp.typography.captionRole,
                        ),
                        elements = skeleton.elements,
                        metadata = com.adnanfoisal.infinitydesign.design.dsl.DocumentMetadata(
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            tags = listOf(bp.mood, bp.visualDirection),
                        ),
                        blueprintId = bp.id,
                        seed = bp.seed,
                    )
                }
            val validated = DesignValidator.validate(best)
            if (validated is AppResult.Err) {
                // Fall back to best-effort save
            }
            val docToSave = (validated as? AppResult.Ok)?.value ?: best
            val saved = repo.save(docToSave, bp, bp.prompt)
            if (saved is AppResult.Ok) onProjectSaved(saved.value)
        }
    }
}
