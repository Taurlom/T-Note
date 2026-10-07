package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class AddScheduledEventUseCase @Inject constructor(private val repository: ScheduledEventRepository) {

    /**
     * Позиция — из базы (max + 1 за день), а не из снапшота UI: иначе два
     * быстрых добавления подряд получали одинаковый position (гонка).
     */
    suspend operator fun invoke(event: ScheduledEvent): Long = repository.add(
        event.copy(position = repository.getMaxPosition(event.date) + 1),
    )
}
