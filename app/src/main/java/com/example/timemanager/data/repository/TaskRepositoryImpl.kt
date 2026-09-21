package com.example.timemanager.data.repository

import com.example.timemanager.data.local.TaskDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.TaskRepository
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
