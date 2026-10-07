package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.repository.CalendarRepository
import javax.inject.Inject

class GetCalendarNotesByMonthUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    operator fun invoke(monthPrefix: String) = repository.getNotesByMonthPrefix(monthPrefix)
}
