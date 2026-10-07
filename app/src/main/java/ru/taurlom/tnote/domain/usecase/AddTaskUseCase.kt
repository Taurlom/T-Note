package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(task: Task): Long =
        repository.insert(task.copy(createdAt = clock.millis()))
}
