package com.example.timemanager.presentation.screens.categories

import com.example.timemanager.domain.model.Category

sealed class CategoriesEvent {
    data class OnCategoryClick(val categoryId: Long) : CategoriesEvent()
    data class OnAddCategory(val name: String, val color: Long) : CategoriesEvent()
    data class OnEditCategory(val category: Category) : CategoriesEvent()
    data class OnDeleteCategory(val category: Category) : CategoriesEvent()
}
