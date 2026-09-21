package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetScheduledEventsByMonthUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    operator fun invoke(monthPrefix: String): Flow<List<ScheduledEvent>> =
        repository.getByMonthPrefix(monthPrefix)
}
