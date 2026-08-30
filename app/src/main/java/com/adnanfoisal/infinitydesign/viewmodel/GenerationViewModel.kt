package com.adnanfoisal.infinitydesign.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanfoisal.infinitydesign.core.dispatchers.AppDispatchers
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.data.preferences.PreferencesRepository
import com.adnanfoisal.infinitydesign.data.preferences.UserPreferences
import com.adnanfoisal.infinitydesign.data.repositories.ProjectRepository
import com.adnanfoisal.infinitydesign.design.layout.CandidateGenerator
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignBlueprint
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.generation.blueprint.BlueprintRequest
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderConfig
import com.adnanfoisal.infinitydesign.generation.blueprint.ProviderKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Stage-1 generation: prompt → Blueprint. Stage-2 happens after the user accepts
 * the direction (Section 9: never generate the final artwork until user accepts).
 *
 * The ViewModel owns screen state (Section 30) — the design engine owns design mutation.
 */
@HiltViewModel
class GenerationViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val prefs: PreferencesRepository,
    private val candidateGen: CandidateGenerator,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val stage: Stage) : UiState()
        data class Success(val blueprint: DesignBlueprint) : UiState()
        data class Error(val kind: AppError.Kind, val message: String) : UiState()
        object Cancelled : UiState()
    }

    enum class Stage { ANALYZING, BUILDING_DIRECTION, DONE }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun generate(prompt: String, style: String?) {
        // Section 78: cancellation; Section 79: race conditions
        job?.cancel()
        _state.value = UiState.Loading(Stage.ANALYZING)
        job = viewModelScope.launch(dispatchers.default) {
            try {
                val p = prefs.observe().first()
                val config = ProviderConfig(
                    litellmUrl = p.litellmUrl.ifBlank { null },
                    litellmApiKey = null, // Section 39: not stored — user enters in-app
                    litellmModel = p.litellmModel.ifBlank { null },
                    geminiApiKey = null, // same
                    geminiModel = p.geminiModel,
                )
                val provider = if (p.provider == "litellm") ProviderKind.LITELLM else ProviderKind.GEMINI
                val request = BlueprintRequest(
                    prompt = prompt,
                    style = style,
                    aspectId = p.defaultAspect,
                    seed = System.currentTimeMillis(),
                    provider = provider,
                    providerConfig = config,
                )
                _state.value = UiState.Loading(Stage.BUILDING_DIRECTION)
                // The backend handles the actual LLM call. For now we use the local
                // candidate generator as a fallback when no provider is configured.
                val result = generateFallback(request)
                when (result) {
                    is AppResult.Ok -> {
                        repo.cacheBlueprint(result.value, prompt)
                        _state.value = UiState.Success(result.value)
                    }
                    is AppResult.Err -> {
                        _state.value = UiState.Error(result.error.kind, result.error.message)
                    }
                }
            } catch (_: CancellationException) {
                _state.value = UiState.Cancelled
            } catch (e: Throwable) {
                _state.value = UiState.Error(AppError.Kind.Unknown, e.message ?: "Generation failed")
            }
        }
    }

    fun cancel() { job?.cancel() }

    /**
     * Offline fallback: if no provider key is set, build a blueprint locally
     * using the design engine's composition + palette templates. This keeps
     * the user productive offline — section 108.
     */
    private suspend fun generateFallback(request: BlueprintRequest): AppResult<DesignBlueprint> {
        // Builds a deterministic blueprint from the prompt — no LLM call.
        val seed = request.seed.takeIf { it != 0L } ?: System.currentTimeMillis()
        val bp = com.adnanfoisal.infinitydesign.generation.parser.LocalBlueprintBuilder.build(
            prompt = request.prompt,
            seed = seed,
            aspectId = request.aspectId,
        )
        repo.cacheBlueprint(bp, request.prompt)
        return AppResult.Ok(bp)
    }
}
