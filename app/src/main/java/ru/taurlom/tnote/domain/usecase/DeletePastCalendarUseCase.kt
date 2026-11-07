package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.repository.CalendarRepository
import ru.taurlom.tnote.domain.repository.PastCalendarCleanup
import javax.inject.Inject

/** «Удалить прошедшие» из настроек календаря: вся чистка — в репозитории. */
class DeletePastCalendarUseCase @Inject constructor(private val repository: CalendarRepository) {
    suspend operator fun invoke(): PastCalendarCleanup = repository.deletePast()
}
