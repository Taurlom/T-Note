package com.example.timemanager.presentation.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.domain.model.Task
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.ReorderableLazyColumn
import com.example.timemanager.presentation.components.TaskInputDialog
import com.example.timemanager.presentation.components.TaskItem
import com.example.timemanager.presentation.theme.AddButtonBackground
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.OnSecondary
import com.example.timemanager.presentation.theme.OnTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    categoryId: Long,
    onBackClick: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(uiState.category?.name ?: stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarBackground,
                    titleContentColor = OnTertiary,
                    navigationIconContentColor = OnTertiary,
                    actionIconContentColor = OnTertiary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(3.dp),
                containerColor = AddButtonBackground,
                contentColor = OnSecondary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_task),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tasks),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ReorderableLazyColumn(
                    items = uiState.tasks,
                    key = { it.id },
                    onReorder = { viewModel.onEvent(TasksEvent.OnReorderTasks(it)) },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) { task, _ ->
                    TaskItem(
                        task = task,
                        onToggleCompletion = {
                            viewModel.onEvent(TasksEvent.OnToggleTaskCompletion(task))
                        },
                        onEdit = { taskToEdit = task },
                        onDelete = { taskToDelete = task },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TaskInputDialog(
            dialogTitle = stringResource(R.string.add_task),
            hasExistingTasks = uiState.tasks.isNotEmpty(),
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description ->
                viewModel.onEvent(TasksEvent.OnAddTask(title, description))
                showAddDialog = false
            },
            onNext = { title, description ->
                viewModel.onEvent(TasksEvent.OnAddTask(title, description))
            }
        )
    }

    taskToEdit?.let { task ->
        TaskInputDialog(
            dialogTitle = stringResource(R.string.edit_task),
            titleInitial = task.title,
            descriptionInitial = task.description,
            onDismiss = { taskToEdit = null },
            onConfirm = { title, description ->
                viewModel.onEvent(
                    TasksEvent.OnEditTask(task.copy(title = title, description = description))
                )
                taskToEdit = null
            }
        )
    }

    taskToDelete?.let { task ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = "Удалить задачу \"${task.title}\"?",
            onDismiss = { taskToDelete = null },
            onConfirm = {
                viewModel.onEvent(TasksEvent.OnDeleteTask(task))
                taskToDelete = null
            }
        )
    }
}
