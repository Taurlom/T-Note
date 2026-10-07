package ru.taurlom.tnote.presentation.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.usecase.DeleteCategoryUseCase
import ru.taurlom.tnote.domain.usecase.GetArchivedCategoriesUseCase
import ru.taurlom.tnote.domain.usecase.RestoreCategoryUseCase
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    getArchivedCategoriesUseCase: GetArchivedCategoriesUseCase,
    private val restoreCategoryUseCase: RestoreCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init {
        getArchivedCategoriesUseCase()
            .onEach { categories -> _uiState.update { it.copy(categories = categories) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ArchiveEvent) {
        when (event) {
            is ArchiveEvent.OnRestore -> viewModelScope.launch {
                restoreCategoryUseCase(event.category)
            }
            is ArchiveEvent.OnDeleteForever -> viewModelScope.launch {
                deleteCategoryUseCase(event.category)
            }
        }
    }
}

data class ArchiveUiState(val categories: List<Category> = emptyList())

sealed interface ArchiveEvent {
    /** Вернуть список из архива — в конец основного списка. */
    data class OnRestore(val category: Category) : ArchiveEvent

    /** Удалить список вместе с задачами — необратимо, за диалогом подтверждения. */
    data class OnDeleteForever(val category: Category) : ArchiveEvent
}
