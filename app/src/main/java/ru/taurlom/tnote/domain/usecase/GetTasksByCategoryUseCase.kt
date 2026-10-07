package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.TaskRepository
import javax.inject.Inject

class GetTasksByCategoryUseCase @Inject constructor(private val repository: TaskRepository) {
    operator fun invoke(categoryId: Long): Flow<List<Task>> = repository.getByCategory(categoryId)
}
