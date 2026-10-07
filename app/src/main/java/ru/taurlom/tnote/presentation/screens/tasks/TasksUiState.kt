package ru.taurlom.tnote.presentation.screens.tasks

import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.model.Task

data class TasksUiState(
    val category: Category? = null,
    val tasks: List<Task> = emptyList(),
    /** Другие списки — цели копирования. */
    val categories: List<Category> = emptyList()
)
