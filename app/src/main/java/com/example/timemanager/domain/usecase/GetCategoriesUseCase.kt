package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> = repository.getAll()
}
