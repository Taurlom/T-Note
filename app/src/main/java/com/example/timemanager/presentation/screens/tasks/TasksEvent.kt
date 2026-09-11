package com.example.timemanager.presentation.screens.tasks

import com.example.timemanager.domain.model.Task

sealed class TasksEvent {
    data class OnAddTask(val title: String, val description: String) : TasksEvent()
    data class OnEditTask(val task: Task) : TasksEvent()
    data class OnDeleteTask(val task: Task) : TasksEvent()
    data class OnToggleTaskCompletion(val task: Task) : TasksEvent()
}
