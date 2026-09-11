package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.TaskRepository
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(task: Task): Long = repository.insert(task)
}
