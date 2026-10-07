package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import javax.inject.Inject

class UpdateScheduledEventUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    suspend operator fun invoke(event: ScheduledEvent) = repository.update(event)
}
