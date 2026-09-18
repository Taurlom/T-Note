package com.example.timemanager.presentation.screens.calendar

import com.example.timemanager.domain.model.CalendarTask

sealed class CalendarEvent {
    data object PreviousMonth : CalendarEvent()
    data object NextMonth : CalendarEvent()
    data class SaveNote(val date: String, val text: String) : CalendarEvent()
    data class DeleteDay(val date: String) : CalendarEvent()
    data class AddTask(val date: String, val text: String) : CalendarEvent()
    data class UpdateTask(val task: CalendarTask) : CalendarEvent()
    data class DeleteTask(val task: CalendarTask) : CalendarEvent()
    data class ToggleTask(val task: CalendarTask) : CalendarEvent()
}
