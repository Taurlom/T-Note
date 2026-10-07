package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

class AddCategoryUseCase @Inject constructor(private val repository: CategoryRepository) {

    /**
     * Позиция — из базы (max + 1 среди активных), а не из снапшота UI:
     * иначе два быстрых добавления подряд получали одинаковый position.
     */
    suspend operator fun invoke(category: Category): Long = repository.insert(
        category.copy(position = repository.getMaxActivePosition() + 1),
    )
}
