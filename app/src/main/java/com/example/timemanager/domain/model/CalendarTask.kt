package com.example.timemanager.domain.model

data class CalendarTask(
    val id: Long = 0,
    val date: String,
    val text: String,
    val isCompleted: Boolean = false,
    val position: Int = 0
)
