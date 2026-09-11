package com.example.timemanager.domain.model

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val categoryId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
