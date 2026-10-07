package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

/** Архивные списки для экрана архива (по имени). */
class GetArchivedCategoriesUseCase @Inject constructor(private val repository: CategoryRepository) {
    operator fun invoke(): Flow<List<Category>> = repository.getArchived()
}
