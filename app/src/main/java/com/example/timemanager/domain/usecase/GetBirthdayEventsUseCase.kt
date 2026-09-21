package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Все дни рождения: они повторяются каждый год и не зависят от просматриваемого месяца. */
class GetBirthdayEventsUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    operator fun invoke(): Flow<List<ScheduledEvent>> =
        repository.getByType(ScheduledEventType.BIRTHDAY)
}
