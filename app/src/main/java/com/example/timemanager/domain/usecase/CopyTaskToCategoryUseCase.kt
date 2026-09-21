package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Копирует задачу в другой список: создаёт новую запись с текущим временем
 * и позицией в конце целевого списка. Копия в тот же список игнорируется.
 */
class CopyTaskToCategoryUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(task: Task, targetCategoryId: Long) {
        if (task.categoryId == targetCategoryId) return
        val nextPosition = taskRepository.getMaxPosition(targetCategoryId) + 1
        taskRepository.insert(
            task.copy(
                id = 0,
                categoryId = targetCategoryId,
                createdAt = System.currentTimeMillis(),
                position = nextPosition
            )
        )
    }
}
