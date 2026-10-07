package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
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
