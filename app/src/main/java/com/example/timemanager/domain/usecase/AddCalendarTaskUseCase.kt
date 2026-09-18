package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.CalendarTask
import com.example.timemanager.domain.repository.CalendarRepository
import javax.inject.Inject

class AddCalendarTaskUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    suspend operator fun invoke(task: CalendarTask) = repository.addTask(task)
}
