package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

class ReorderCategoriesUseCase @Inject constructor(
    private val repository: CategoryRepository
) {
    suspend operator fun invoke(categories: List<Category>) =
        repository.updatePositions(categories)
}
