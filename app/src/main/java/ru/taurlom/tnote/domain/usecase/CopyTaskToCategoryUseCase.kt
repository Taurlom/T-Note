package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Копирует задачу в другой список: создаёт новую запись с текущим временем
 * и позицией в конце целевого списка. Копия в тот же список игнорируется.
 */
class CopyTaskToCategoryUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(task: Task, targetCategoryId: Long) {
        if (task.categoryId == targetCategoryId) return
        val nextPosition = taskRepository.getMaxPosition(targetCategoryId) + 1
        taskRepository.insert(
            task.copy(
                id = 0,
                categoryId = targetCategoryId,
                createdAt = clock.millis(),
                position = nextPosition
            )
        )
    }
}
