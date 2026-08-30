package com.adnanfoisal.infinitydesign.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adnanfoisal.infinitydesign.core.dispatchers.AppDispatchers
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.data.database.ProjectSummary
import com.adnanfoisal.infinitydesign.data.repositories.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ProjectRepository,
    private val dispatchers: AppDispatchers,
) : ViewModel() {
    val projects: StateFlow<List<ProjectSummary>> = repo.observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch(dispatchers.io) { repo.delete(id) }
    }
}
