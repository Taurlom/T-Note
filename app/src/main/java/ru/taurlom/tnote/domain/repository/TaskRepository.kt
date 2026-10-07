package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Task

interface TaskRepository {

    fun getByCategory(categoryId: Long): Flow<List<Task>>
    suspend fun getMaxPosition(categoryId: Long): Int
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun updatePositions(tasks: List<Task>)
}
