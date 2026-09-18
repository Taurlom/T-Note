package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.repository.CalendarRepository
import javax.inject.Inject

class GetCalendarNotesByMonthUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    operator fun invoke(monthPrefix: String) = repository.getNotesByMonthPrefix(monthPrefix)
}
