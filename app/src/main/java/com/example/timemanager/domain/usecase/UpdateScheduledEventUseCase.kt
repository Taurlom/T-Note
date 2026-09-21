package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class UpdateScheduledEventUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    suspend operator fun invoke(event: ScheduledEvent) = repository.update(event)
}
