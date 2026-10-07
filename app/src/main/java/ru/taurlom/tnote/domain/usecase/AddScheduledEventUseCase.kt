package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class AddScheduledEventUseCase @Inject constructor(private val repository: ScheduledEventRepository) {
    suspend operator fun invoke(event: ScheduledEvent): Long = repository.add(event)
}
