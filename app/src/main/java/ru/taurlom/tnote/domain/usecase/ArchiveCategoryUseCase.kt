package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * Переместить список в архив: скрывается из основного перечня, задачи
 * внутри остаются нетронутыми. Обратимо — см. [RestoreCategoryUseCase].
 */
class ArchiveCategoryUseCase @Inject constructor(private val repository: CategoryRepository) {
    suspend operator fun invoke(category: Category) = repository.archive(category.id)
}
