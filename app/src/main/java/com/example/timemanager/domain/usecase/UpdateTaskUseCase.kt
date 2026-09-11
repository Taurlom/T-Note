package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.TaskRepository
import javax.inject.Inject

class UpdateTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(task: Task) = repository.update(task)
}
