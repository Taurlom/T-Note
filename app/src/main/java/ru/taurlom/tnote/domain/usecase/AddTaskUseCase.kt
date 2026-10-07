package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(private val repository: TaskRepository, private val clock: Clock) {

    /**
     * Позиция — из базы (max + 1), а не из снапшота UI: иначе два быстрых
     * добавления подряд получали одинаковый position (гонка).
     */
    suspend operator fun invoke(task: Task): Long = repository.insert(
        task.copy(
            createdAt = clock.millis(),
            position = repository.getMaxPosition(task.categoryId) + 1,
        ),
    )
}
