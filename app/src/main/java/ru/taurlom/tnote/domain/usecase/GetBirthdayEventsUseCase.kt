package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Все дни рождения: они повторяются каждый год и не зависят от просматриваемого месяца. */
class GetBirthdayEventsUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    operator fun invoke(): Flow<List<ScheduledEvent>> =
        repository.getByType(ScheduledEventType.BIRTHDAY)
}
