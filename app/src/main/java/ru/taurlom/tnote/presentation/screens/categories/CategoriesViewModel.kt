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
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.ListShareRepository
import ru.taurlom.tnote.domain.usecase.AddCategoryUseCase
import ru.taurlom.tnote.domain.usecase.ArchiveCategoryUseCase
import ru.taurlom.tnote.domain.usecase.GetCategoriesUseCase
import ru.taurlom.tnote.domain.usecase.ImportSharedListUseCase
import ru.taurlom.tnote.domain.usecase.ReorderCategoriesUseCase
import ru.taurlom.tnote.domain.usecase.RestoreCategoryUseCase
import ru.taurlom.tnote.domain.usecase.UpdateCategoryUseCase
import ru.taurlom.tnote.presentation.error.launchCatching
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addCategoryUseCase: AddCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val archiveCategoryUseCase: ArchiveCategoryUseCase,
    private val restoreCategoryUseCase: RestoreCategoryUseCase,
    private val reorderCategoriesUseCase: ReorderCategoriesUseCase,
    private val listShareRepository: ListShareRepository,
    private val importSharedListUseCase: ImportSharedListUseCase,
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
                launchCatching {
                    // position заполнит AddCategoryUseCase: позиция считается
                    // по базе, а не по снапшоту UI (гонка двух быстрых добавлений).
                    addCategoryUseCase(
                        Category(
                            name = event.name.trim(),
                            color = event.color,
                        ),
                    )
                }
            }

            is CategoriesEvent.OnEditCategory -> {
                launchCatching {
                    updateCategoryUseCase(event.category)
                }
            }

            is CategoriesEvent.OnArchiveCategory -> {
                launchCatching {
                    archiveCategoryUseCase(event.category)
                }
            }

            is CategoriesEvent.OnRestoreCategory -> {
                launchCatching {
                    restoreCategoryUseCase(event.category)
                }
            }

            is CategoriesEvent.OnReorderCategories -> {
                launchCatching {
                    val reordered = event.categories.mapIndexed { index, category ->
                        category.copy(position = index)
                    }
                    reorderCategoriesUseCase(reordered)
                }
            }

            is CategoriesEvent.OnReadSharedList -> {
                launchCatching {
                    val shared = runCatching {
                        listShareRepository.importFromFile(event.uri)
                    }.getOrNull()
                    _uiState.update {
                        it.copy(
                            incomingShare = shared,
                            shareFeedback =
                            if (shared == null) ShareFeedback.Failed else null,
                        )
                    }
                }
            }

            is CategoriesEvent.OnConfirmImportSharedList -> {
                launchCatching {
                    val shared = _uiState.value.incomingShare ?: return@launchCatching
                    runCatching {
                        importSharedListUseCase(shared)
                    }.onSuccess {
                        _uiState.update {
                            it.copy(
                                incomingShare = null,
                                shareFeedback = ShareFeedback.Imported(shared.name),
                            )
                        }
                    }.onFailure {
                        _uiState.update {
                            it.copy(
                                incomingShare = null,
                                shareFeedback = ShareFeedback.Failed,
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
