package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.repository.CalendarRepository
import javax.inject.Inject

class DeleteCalendarDayUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    suspend operator fun invoke(date: String) = repository.deleteDayData(date)
}
