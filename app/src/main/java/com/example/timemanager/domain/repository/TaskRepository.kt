package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {

    fun getByCategory(categoryId: Long): Flow<List<Task>>
    suspend fun insert(task: Task): Long
    suspend fun update(task: Task)
    suspend fun delete(task: Task)
    suspend fun updatePositions(tasks: List<Task>)
}
