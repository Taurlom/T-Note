package com.example.timemanager.presentation.screens.tasks

import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.model.Task

data class TasksUiState(
    val category: Category? = null,
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
