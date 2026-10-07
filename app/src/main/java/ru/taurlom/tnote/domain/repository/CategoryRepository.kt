package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.model.Task

interface CategoryRepository {

    /** Только активные списки — архивные возвращает [getArchived]. */
    fun getAll(): Flow<List<Category>>
    fun getArchived(): Flow<List<Category>>

    /** Максимальная позиция среди активных списков — для вставки «в конец». */
    suspend fun getMaxActivePosition(): Int

    suspend fun insert(category: Category): Long

    /**
     * Атомарная вставка списка целиком (импорт `.tnote`): категория и её
     * задачи пишутся одной транзакцией, categoryId задач подставляется
     * после вставки категории. Сбой не оставляет ни пустой категории,
     * ни задач-сирот.
     */
    suspend fun insertWithTasks(category: Category, tasks: List<Task>): Long

    suspend fun update(category: Category)
    suspend fun delete(category: Category)
    suspend fun updatePositions(categories: List<Category>)

    /** Переместить в архив: список скрывается, задачи внутри целы. */
    suspend fun archive(categoryId: Long)

    /** Вернуть из архива в конец основного списка. */
    suspend fun restore(categoryId: Long)
}
