package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetCategoryByIdUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    operator fun invoke(id: Long): Flow<Category?> {
        return repository.getAll().map { list -> list.find { it.id == id } }
    }
}
