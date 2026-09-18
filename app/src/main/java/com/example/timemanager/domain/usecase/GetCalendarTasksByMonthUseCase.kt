package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.repository.CalendarRepository
import javax.inject.Inject

class GetCalendarTasksByMonthUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    operator fun invoke(monthPrefix: String) = repository.getTasksByMonthPrefix(monthPrefix)
}
