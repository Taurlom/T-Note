package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.repository.CategoryRepository
import javax.inject.Inject

class UpdateCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    suspend operator fun invoke(category: Category) = repository.update(category)
}
