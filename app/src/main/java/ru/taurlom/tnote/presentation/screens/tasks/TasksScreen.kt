package ru.taurlom.tnote.presentation.screens.tasks

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.Task
import ru.taurlom.tnote.presentation.components.AppButton
import ru.taurlom.tnote.presentation.components.AppDialog
import ru.taurlom.tnote.presentation.components.AppFab
import ru.taurlom.tnote.presentation.components.AppTopBar
import ru.taurlom.tnote.presentation.components.ConfirmDeleteDialog
import ru.taurlom.tnote.presentation.components.ReorderableLazyColumn
import ru.taurlom.tnote.presentation.components.TaskInputDialog
import ru.taurlom.tnote.presentation.components.TaskItem
import ru.taurlom.tnote.presentation.util.shareList
import ru.taurlom.tnote.presentation.util.shareListFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(categoryId: Long, onBackClick: () -> Unit, viewModel: TasksViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Флаги диалогов — saveable: пересоздание Activity не закрывает
    // открытый диалог. Объекты редактирования/удаления — обычный
    // remember: без Parcelable/Saver они не сохраняются, а потеря
    // мала — диалог закроется, пользователь тапнет задачу заново.
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = uiState.category?.name ?: stringResource(R.string.tasks_title),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    // Список ещё грузится (category == null) — делиться нечем.
                    if (uiState.category != null) {
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = stringResource(R.string.share_list),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AppFab(
                onClick = { showAddDialog = true },
                contentDescriptionRes = R.string.add_task,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_tasks),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                ReorderableLazyColumn(
                    items = uiState.tasks,
                    key = { it.id },
                    onReorder = { viewModel.onEvent(TasksEvent.OnReorderTasks(it)) },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) { task, _ ->
                    TaskItem(
                        task = task,
                        onToggleCompletion = {
                            viewModel.onEvent(TasksEvent.OnToggleTaskCompletion(task))
                        },
                        onEdit = { taskToEdit = task },
                        onDelete = { taskToDelete = task },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TaskInputDialog(
            dialogTitle = stringResource(R.string.add_task),
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description ->
                viewModel.onEvent(TasksEvent.OnAddTask(title, description))
                showAddDialog = false
            },
            onNext = { title, description ->
                viewModel.onEvent(TasksEvent.OnAddTask(title, description))
            },
        )
    }

    taskToEdit?.let { task ->
        TaskInputDialog(
            dialogTitle = stringResource(R.string.edit_task),
            titleInitial = task.title,
            descriptionInitial = task.description,
            copyTargets = uiState.categories,
            onCopyTo = { targetId, title, description ->
                viewModel.onEvent(
                    TasksEvent.OnCopyTask(
                        task.copy(title = title.trim(), description = description.trim()),
                        targetId,
                    ),
                )
            },
            onDismiss = { taskToEdit = null },
            onConfirm = { title, description ->
                viewModel.onEvent(
                    TasksEvent.OnEditTask(task.copy(title = title, description = description)),
                )
                taskToEdit = null
            },
        )
    }

    taskToDelete?.let { task ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_task_confirm, task.title),
            onDismiss = { taskToDelete = null },
            onConfirm = {
                viewModel.onEvent(TasksEvent.OnDeleteTask(task))
                taskToDelete = null
            },
        )
    }

    if (showShareDialog) {
        AppDialog(
            title = stringResource(R.string.share_list),
            onDismissRequest = { showShareDialog = false },
            text = {
                AppButton(
                    onClick = {
                        showShareDialog = false
                        shareList(
                            context = context,
                            categoryName = uiState.category?.name.orEmpty(),
                            tasks = uiState.tasks,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share_list_text))
                }
                AppButton(
                    onClick = {
                        showShareDialog = false
                        scope.launch {
                            val uri = viewModel.createShareFileUri()
                            if (uri != null) {
                                shareListFile(context, uri)
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.share_list_file_error,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share_list_file))
                }
            },
            confirmButton = {},
        )
    }
}
