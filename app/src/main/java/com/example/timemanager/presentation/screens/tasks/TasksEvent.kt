package com.example.timemanager.presentation.screens.tasks

import com.example.timemanager.domain.model.Task

sealed class TasksEvent {
    data class OnAddTask(val title: String, val description: String) : TasksEvent()
    data class OnEditTask(val task: Task) : TasksEvent()

    /** Копия задачи [task] (с текущими полями редактора) в список [targetCategoryId]. */
    data class OnCopyTask(val task: Task, val targetCategoryId: Long) : TasksEvent()
    data class OnDeleteTask(val task: Task) : TasksEvent()
    data class OnToggleTaskCompletion(val task: Task) : TasksEvent()
    data class OnReorderTasks(val tasks: List<Task>) : TasksEvent()
}
