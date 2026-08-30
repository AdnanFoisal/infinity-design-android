package com.adnanfoisal.infinitydesign.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanfoisal.infinitydesign.core.dispatchers.AppDispatchers
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.data.preferences.PreferencesRepository
import com.adnanfoisal.infinitydesign.data.preferences.UserPreferences
import com.adnanfoisal.infinitydesign.data.repositories.ProjectRepository
import com.adnanfoisal.infinitydesign.design.commands.DesignCommand
import com.adnanfoisal.infinitydesign.design.history.DesignHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        object NotFound : UiState()
        data class Ready(val history: DesignHistory) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var history: DesignHistory? = null

    fun load(projectId: String) {
        viewModelScope.launch(dispatchers.io) {
            when (val r = repo.load(projectId)) {
                is AppResult.Ok -> {
                    val h = DesignHistory(r.value.document)
                    history = h
                    _state.value = UiState.Ready(h)
                }
                is AppResult.Err -> {
                    _state.value = UiState.Error(r.error.message)
                }
            }
        }
    }

    fun pushCommand(cmd: DesignCommand) {
        val h = history ?: return
        viewModelScope.launch(dispatchers.default) {
            h.push(cmd)
            _state.value = UiState.Ready(h)
        }
    }

    fun undo() {
        val h = history ?: return
        viewModelScope.launch(dispatchers.default) {
            h.undo()
            _state.value = UiState.Ready(h)
        }
    }

    fun redo() {
        val h = history ?: return
        viewModelScope.launch(dispatchers.default) {
            h.redo()
            _state.value = UiState.Ready(h)
        }
    }

    fun save(onSaved: () -> Unit) {
        val h = history ?: return
        val doc = h.state.value
        viewModelScope.launch(dispatchers.io) {
            repo.save(doc)
            onSaved()
        }
    }

    val canUndo: Boolean get() = history?.canUndo ?: false
    val canRedo: Boolean get() = history?.canRedo ?: false
}
