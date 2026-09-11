package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTasksByCategoryUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    operator fun invoke(categoryId: Long): Flow<List<Task>> = repository.getByCategory(categoryId)
}
