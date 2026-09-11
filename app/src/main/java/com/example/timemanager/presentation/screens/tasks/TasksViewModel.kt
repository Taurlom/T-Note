package com.example.timemanager.presentation.screens.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.usecase.AddTaskUseCase
import com.example.timemanager.domain.usecase.DeleteTaskUseCase
import com.example.timemanager.domain.usecase.GetCategoryByIdUseCase
import com.example.timemanager.domain.usecase.GetTasksByCategoryUseCase
import com.example.timemanager.domain.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTasksByCategoryUseCase: GetTasksByCategoryUseCase,
    private val getCategoryByIdUseCase: GetCategoryByIdUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase
) : ViewModel() {

    private val categoryId: Long = checkNotNull(savedStateHandle["categoryId"])

    private val _uiState = MutableStateFlow(TasksUiState(isLoading = true))
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        loadCategory()
        loadTasks()
    }

    private fun loadCategory() {
        getCategoryByIdUseCase(categoryId)
            .onEach { category ->
                _uiState.update { it.copy(category = category) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadTasks() {
        getTasksByCategoryUseCase(categoryId)
            .onEach { tasks ->
                _uiState.update { it.copy(tasks = tasks, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: TasksEvent) {
        when (event) {
            is TasksEvent.OnAddTask -> {
                viewModelScope.launch {
                    addTaskUseCase(
                        Task(
                            title = event.title.trim(),
                            description = event.description.trim(),
                            categoryId = categoryId
                        )
                    )
                }
            }
            is TasksEvent.OnEditTask -> {
                viewModelScope.launch {
                    updateTaskUseCase(event.task)
                }
            }
            is TasksEvent.OnDeleteTask -> {
                viewModelScope.launch {
                    deleteTaskUseCase(event.task)
                }
            }
            is TasksEvent.OnToggleTaskCompletion -> {
                viewModelScope.launch {
                    updateTaskUseCase(event.task.copy(isCompleted = !event.task.isCompleted))
                }
            }
        }
    }
}
