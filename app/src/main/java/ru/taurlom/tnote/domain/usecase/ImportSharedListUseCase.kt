package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.first
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.model.SharedList
import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.CategoryRepository
import ru.taurlom.tnote.domain.repository.TaskRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Добавляет список, полученный из файла `.tnote`, как новую категорию:
 * id создаются заново (автоинкремент Room), порядок пунктов сохраняется,
 * категория встаёт в конец. Цвет без явного указания — доменный
 * [Category.DEFAULT_COLOR], а не параметр из UI.
 */
class ImportSharedListUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(shared: SharedList): Long {
        val nextPosition =
            (categoryRepository.getAll().first().maxOfOrNull { it.position } ?: -1) + 1
        val categoryId = categoryRepository.insert(
            Category(
                name = shared.name,
                color = shared.color ?: Category.DEFAULT_COLOR,
                position = nextPosition,
            ),
        )
        val now = clock.millis()
        shared.tasks.forEachIndexed { index, sharedTask ->
            taskRepository.insert(
                Task(
                    title = sharedTask.title,
                    description = sharedTask.description,
                    isCompleted = sharedTask.completed,
                    categoryId = categoryId,
                    createdAt = now,
                    position = index,
                ),
            )
        }
        return categoryId
    }
}
