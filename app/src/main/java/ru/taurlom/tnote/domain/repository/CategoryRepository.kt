package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Category

interface CategoryRepository {

    /** Только активные списки — архивные возвращает [getArchived]. */
    fun getAll(): Flow<List<Category>>
    fun getArchived(): Flow<List<Category>>
    suspend fun insert(category: Category): Long
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
    suspend fun updatePositions(categories: List<Category>)

    /** Переместить в архив: список скрывается, задачи внутри целы. */
    suspend fun archive(categoryId: Long)

    /** Вернуть из архива в конец основного списка. */
    suspend fun restore(categoryId: Long)
}
