package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> = repository.getAll()
}
