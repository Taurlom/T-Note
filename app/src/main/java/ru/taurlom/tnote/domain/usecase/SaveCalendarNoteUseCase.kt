package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.repository.CalendarRepository
import javax.inject.Inject

class SaveCalendarNoteUseCase @Inject constructor(private val repository: CalendarRepository) {
    suspend operator fun invoke(note: CalendarNote) = repository.saveNote(note)
}
