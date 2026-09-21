package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class AddScheduledEventUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    suspend operator fun invoke(event: ScheduledEvent): Long = repository.add(event)
}
