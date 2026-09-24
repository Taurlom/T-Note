package com.example.timemanager.presentation.screens.tasks

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timemanager.domain.model.SharedList
import com.example.timemanager.domain.model.SharedTask
import com.example.timemanager.domain.model.Task
import com.example.timemanager.domain.repository.ListShareRepository
import com.example.timemanager.domain.usecase.AddTaskUseCase
import com.example.timemanager.domain.usecase.CopyTaskToCategoryUseCase
import com.example.timemanager.domain.usecase.DeleteTaskUseCase
import com.example.timemanager.domain.usecase.GetCategoriesUseCase
import com.example.timemanager.domain.usecase.GetCategoryByIdUseCase
import com.example.timemanager.domain.usecase.GetTasksByCategoryUseCase
import com.example.timemanager.domain.usecase.ReorderTasksUseCase
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
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val reorderTasksUseCase: ReorderTasksUseCase,
    private val copyTaskToCategoryUseCase: CopyTaskToCategoryUseCase,
    private val listShareRepository: ListShareRepository
) : ViewModel() {

    private val categoryId: Long = checkNotNull(savedStateHandle["categoryId"])

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        loadCategory()
        loadTasks()
        loadOtherCategories()
    }

    private fun loadCategory() {
        getCategoryByIdUseCase(categoryId)
            .onEach { category ->
                _uiState.update { it.copy(category = category) }
            }
            .launchIn(viewModelScope)
    }

    /** Списки-цели для «Копировать в»: все, кроме текущего. */
    private fun loadOtherCategories() {
        getCategoriesUseCase()
            .onEach { categories ->
                _uiState.update {
                    it.copy(categories = categories.filterNot { c -> c.id == categoryId })
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadTasks() {
        getTasksByCategoryUseCase(categoryId)
            .onEach { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Файл `.tnote` текущего списка для «Поделиться файлом».
     * null — список ещё не загрузился или не удалось записать файл.
     */
    suspend fun createShareFileUri(): Uri? {
        val snapshot = _uiState.value
        val category = snapshot.category ?: return null
        val shared = SharedList(
            name = category.name,
            color = category.color,
            tasks = snapshot.tasks.map {
                SharedTask(
                    title = it.title,
                    description = it.description,
                    completed = it.isCompleted
                )
            }
        )
        return runCatching { listShareRepository.exportToFile(shared) }.getOrNull()
    }

    fun onEvent(event: TasksEvent) {
        when (event) {
            is TasksEvent.OnAddTask -> {
                viewModelScope.launch {
                    val nextPosition = (_uiState.value.tasks.maxOfOrNull { it.position } ?: -1) + 1
                    addTaskUseCase(
                        Task(
                            title = event.title.trim(),
                            description = event.description.trim(),
                            categoryId = categoryId,
                            position = nextPosition
                        )
                    )
                }
            }
            is TasksEvent.OnEditTask -> {
                viewModelScope.launch {
                    updateTaskUseCase(event.task)
                }
            }
            is TasksEvent.OnCopyTask -> {
                viewModelScope.launch {
                    copyTaskToCategoryUseCase(event.task, event.targetCategoryId)
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
            is TasksEvent.OnReorderTasks -> {
                viewModelScope.launch {
                    val reordered = event.tasks.mapIndexed { index, task ->
                        task.copy(position = index)
                    }
                    reorderTasksUseCase(reordered)
                }
            }
        }
    }
}
