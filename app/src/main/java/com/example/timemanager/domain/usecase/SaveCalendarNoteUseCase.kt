package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.CalendarNote
import com.example.timemanager.domain.repository.CalendarRepository
import javax.inject.Inject

class SaveCalendarNoteUseCase @Inject constructor(
    private val repository: CalendarRepository
) {
    suspend operator fun invoke(note: CalendarNote) = repository.saveNote(note)
}
