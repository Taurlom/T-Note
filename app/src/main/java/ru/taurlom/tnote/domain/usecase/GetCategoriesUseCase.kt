package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(private val repository: CategoryRepository) {
    operator fun invoke(): Flow<List<Category>> = repository.getAll()
}
