package ru.taurlom.tnote.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.local.CategoryDao
import ru.taurlom.tnote.data.local.TaskDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val dao: CategoryDao,
    private val taskDao: TaskDao,
) : CategoryRepository {

    override fun getAll(): Flow<List<Category>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getArchived(): Flow<List<Category>> = dao.getArchived().map { list -> list.map { it.toDomain() } }

    override suspend fun getMaxActivePosition(): Int = dao.getMaxActivePosition()

    override suspend fun insert(category: Category): Long = dao.insert(category.toEntity())

    /**
     * Импорт «категория + её задачи» — одной транзакцией: сбой посередине
     * не оставляет ни пустой категории, ни задач-сирот. categoryId задач
     * подставляется после вставки категории.
     */
    override suspend fun insertWithTasks(category: Category, tasks: List<Task>): Long = database.withTransaction {
        val categoryId = dao.insert(category.toEntity())
        tasks.forEach { taskDao.insert(it.toEntity().copy(categoryId = categoryId)) }
        categoryId
    }

    override suspend fun update(category: Category) = dao.update(category.id, category.name, category.color, category.position)

    override suspend fun delete(category: Category) = dao.delete(category.toEntity())

    override suspend fun updatePositions(categories: List<Category>) = dao.updatePositions(categories.map { it.toEntity() })

    override suspend fun archive(categoryId: Long) = dao.archive(categoryId)

    override suspend fun restore(categoryId: Long) = dao.restore(categoryId)
}
