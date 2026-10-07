package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.model.SharedList
import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.domain.repository.CategoryRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Добавляет список, полученный из файла `.tnote`, как новую категорию:
 * id создаются заново (автоинкремент Room), порядок пунктов сохраняется,
 * категория встаёт в конец. Цвет без явного указания — доменный
 * [Category.DEFAULT_COLOR], а не параметр из UI.
 */
class ImportSharedListUseCase @Inject constructor(private val categoryRepository: CategoryRepository, private val clock: Clock) {

    /**
     * Вставка атомарна (см. [CategoryRepository.insertWithTasks]): сбой
     * посередине не оставляет ни пустой категории, ни задач-сирот.
     * Позиция категории — из базы (max + 1), а не из снапшота UI.
     */
    suspend operator fun invoke(shared: SharedList): Long {
        val now = clock.millis()
        val tasks = shared.tasks.mapIndexed { index, sharedTask ->
            Task(
                title = sharedTask.title,
                description = sharedTask.description,
                isCompleted = sharedTask.completed,
                // categoryId подставит репозиторий после вставки категории.
                categoryId = 0,
                createdAt = now,
                position = index,
            )
        }
        return categoryRepository.insertWithTasks(
            Category(
                name = shared.name,
                color = shared.color ?: Category.DEFAULT_COLOR,
                position = categoryRepository.getMaxActivePosition() + 1,
            ),
            tasks,
        )
    }
}
