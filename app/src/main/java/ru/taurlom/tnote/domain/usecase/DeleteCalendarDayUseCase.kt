package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.repository.CalendarRepository
import javax.inject.Inject

class DeleteCalendarDayUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    suspend operator fun invoke(date: String) = repository.deleteDayData(date)
}
