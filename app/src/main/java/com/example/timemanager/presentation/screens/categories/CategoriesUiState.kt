package com.example.timemanager.presentation.screens.categories

import com.example.timemanager.domain.model.Category

data class CategoriesUiState(
    val categories: List<Category> = emptyList()
)
