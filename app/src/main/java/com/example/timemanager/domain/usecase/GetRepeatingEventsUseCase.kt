package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Все повторяющиеся события: их вхождения разворачиваются на каждый
 * отображаемый месяц, поэтому запрашиваются глобально, а не по месяцу.
 */
class GetRepeatingEventsUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    operator fun invoke(): Flow<List<ScheduledEvent>> =
        repository.getByType(ScheduledEventType.REPEATING)
}
