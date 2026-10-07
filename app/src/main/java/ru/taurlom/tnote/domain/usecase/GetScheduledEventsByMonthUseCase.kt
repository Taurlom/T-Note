package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class GetScheduledEventsByMonthUseCase @Inject constructor(private val repository: ScheduledEventRepository) {
    operator fun invoke(monthPrefix: String): Flow<List<ScheduledEvent>> = repository.getByMonthPrefix(monthPrefix)
}
