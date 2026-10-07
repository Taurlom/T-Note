package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * Вернуть список из архива — в конец основного списка: прежнюю позицию
 * могли занять перетаскиванием, пока список был архивным.
 */
class RestoreCategoryUseCase @Inject constructor(private val repository: CategoryRepository) {
    suspend operator fun invoke(category: Category) = repository.restore(category.id)
}
