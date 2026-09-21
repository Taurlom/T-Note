package com.example.timemanager.presentation.screens.calendar

import com.example.timemanager.domain.model.ScheduledEvent

sealed class CalendarEvent {
    data object PreviousMonth : CalendarEvent()
    data object NextMonth : CalendarEvent()
    data class SaveNote(val date: String, val text: String) : CalendarEvent()
    data class DeleteDay(val date: String) : CalendarEvent()

    /** Добавление события: [date] — день диалога, [event] — черновик из редактора. */
    data class AddEvent(val date: String, val event: ScheduledEvent) : CalendarEvent()
    data class UpdateEvent(val event: ScheduledEvent) : CalendarEvent()
    data class DeleteEvent(val event: ScheduledEvent) : CalendarEvent()
}
