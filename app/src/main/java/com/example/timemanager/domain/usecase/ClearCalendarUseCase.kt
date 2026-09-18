package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.repository.CalendarRepository
import javax.inject.Inject

class ClearCalendarUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    suspend operator fun invoke() = repository.clearCalendar()
}
