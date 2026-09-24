package com.example.timemanager.presentation.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.repository.ListShareRepository
import com.example.timemanager.domain.usecase.AddCategoryUseCase
import com.example.timemanager.domain.usecase.DeleteCategoryUseCase
import com.example.timemanager.domain.usecase.GetCategoriesUseCase
import com.example.timemanager.domain.usecase.ImportSharedListUseCase
import com.example.timemanager.domain.usecase.ReorderCategoriesUseCase
import com.example.timemanager.domain.usecase.UpdateCategoryUseCase
import com.example.timemanager.presentation.components.defaultCategoryColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addCategoryUseCase: AddCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
    private val reorderCategoriesUseCase: ReorderCategoriesUseCase,
    private val listShareRepository: ListShareRepository,
    private val importSharedListUseCase: ImportSharedListUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        getCategories()
    }

    private fun getCategories() {
        getCategoriesUseCase()
            .onEach { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            is CategoriesEvent.OnCategoryClick -> {
                // handled in UI layer
            }
            is CategoriesEvent.OnAddCategory -> {
                viewModelScope.launch {
                    val nextPosition = (_uiState.value.categories.maxOfOrNull { it.position } ?: -1) + 1
                    addCategoryUseCase(
                        Category(
                            name = event.name.trim(),
                            color = event.color,
                            position = nextPosition
                        )
                    )
                }
            }
            is CategoriesEvent.OnEditCategory -> {
                viewModelScope.launch {
                    updateCategoryUseCase(event.category)
                }
            }
            is CategoriesEvent.OnDeleteCategory -> {
                viewModelScope.launch {
                    deleteCategoryUseCase(event.category)
                }
            }
            is CategoriesEvent.OnReorderCategories -> {
                viewModelScope.launch {
                    val reordered = event.categories.mapIndexed { index, category ->
                        category.copy(position = index)
                    }
                    reorderCategoriesUseCase(reordered)
                }
            }
            is CategoriesEvent.OnReadSharedList -> {
                viewModelScope.launch {
                    val shared = runCatching {
                        listShareRepository.importFromFile(event.uri)
                    }.getOrNull()
                    _uiState.update {
                        it.copy(
                            incomingShare = shared,
                            shareFeedback =
                                if (shared == null) ShareFeedback.Failed else null
                        )
                    }
                }
            }
            is CategoriesEvent.OnConfirmImportSharedList -> {
                viewModelScope.launch {
                    val shared = _uiState.value.incomingShare ?: return@launch
                    runCatching {
                        importSharedListUseCase(shared, defaultCategoryColor())
                    }.onSuccess {
                        _uiState.update {
                            it.copy(
                                incomingShare = null,
                                shareFeedback = ShareFeedback.Imported(shared.name)
                            )
                        }
                    }.onFailure {
                        _uiState.update {
                            it.copy(
                                incomingShare = null,
                                shareFeedback = ShareFeedback.Failed
                            )
                        }
                    }
                }
            }
            is CategoriesEvent.OnDismissImportSharedList -> {
                _uiState.update { it.copy(incomingShare = null) }
            }
            is CategoriesEvent.OnShareFeedbackShown -> {
                _uiState.update { it.copy(shareFeedback = null) }
            }
        }
    }
}
