package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

class GetCategoryByIdUseCase @Inject constructor(private val repository: CategoryRepository) {
    operator fun invoke(id: Long): Flow<Category?> = repository.getAll().map { list -> list.find { it.id == id } }
}
