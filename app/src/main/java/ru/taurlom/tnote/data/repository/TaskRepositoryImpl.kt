package ru.taurlom.tnote.data.repository

import ru.taurlom.tnote.data.local.TaskDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao
) : TaskRepository {

    override fun getByCategory(categoryId: Long): Flow<List<Task>> =
        dao.getByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMaxPosition(categoryId: Long): Int =
        dao.getMaxPosition(categoryId)

    override suspend fun insert(task: Task): Long =
        dao.insert(task.toEntity())

    override suspend fun update(task: Task) =
        dao.update(task.toEntity())

    override suspend fun delete(task: Task) =
        dao.delete(task.toEntity())

    override suspend fun updatePositions(tasks: List<Task>) {
        tasks.forEach { dao.update(it.toEntity()) }
    }
}
