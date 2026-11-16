package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import java.time.YearMonth
import javax.inject.Inject

class GetScheduledEventsByMonthUseCase @Inject constructor(private val repository: ScheduledEventRepository) {
    operator fun invoke(month: YearMonth): Flow<List<ScheduledEvent>> = repository.getByMonth(month)
}
