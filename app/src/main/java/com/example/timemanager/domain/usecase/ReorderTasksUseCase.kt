package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.TaskRepository
import javax.inject.Inject

class ReorderTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(tasks: List<Task>) =
        repository.updatePositions(tasks)
}
