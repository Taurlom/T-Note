package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.TaskRepository
import javax.inject.Inject

class ReorderTasksUseCase @Inject constructor(private val repository: TaskRepository) {
    suspend operator fun invoke(tasks: List<Task>) = repository.updatePositions(tasks)
}
