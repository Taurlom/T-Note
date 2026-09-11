package com.example.timemanager.presentation.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.usecase.AddCategoryUseCase
import com.example.timemanager.domain.usecase.DeleteCategoryUseCase
import com.example.timemanager.domain.usecase.GetCategoriesUseCase
import com.example.timemanager.domain.usecase.UpdateCategoryUseCase
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
    private val deleteCategoryUseCase: DeleteCategoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState(isLoading = true))
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        getCategories()
    }

    private fun getCategories() {
        getCategoriesUseCase()
            .onEach { categories ->
                _uiState.update { it.copy(categories = categories, isLoading = false) }
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
                    addCategoryUseCase(
                        Category(name = event.name.trim(), color = event.color)
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
        }
    }
}
