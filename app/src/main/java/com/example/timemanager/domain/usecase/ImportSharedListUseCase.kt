package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.model.SharedList
import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.CategoryRepository
import com.example.timemanager.domain.repository.TaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Добавляет список, полученный из файла `.tnote`, как новую категорию:
 * id создаются заново (автоинкремент Room), порядок пунктов сохраняется,
 * категория встаёт в конец.
 */
class ImportSharedListUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepository
) {

    suspend operator fun invoke(shared: SharedList, fallbackColor: Long): Long {
        val nextPosition =
            (categoryRepository.getAll().first().maxOfOrNull { it.position } ?: -1) + 1
        val categoryId = categoryRepository.insert(
            Category(
                name = shared.name,
                color = shared.color ?: fallbackColor,
                position = nextPosition
            )
        )
        shared.tasks.forEachIndexed { index, sharedTask ->
            taskRepository.insert(
                Task(
                    title = sharedTask.title,
                    description = sharedTask.description,
                    isCompleted = sharedTask.completed,
                    categoryId = categoryId,
                    position = index
                )
            )
        }
        return categoryId
    }
}
