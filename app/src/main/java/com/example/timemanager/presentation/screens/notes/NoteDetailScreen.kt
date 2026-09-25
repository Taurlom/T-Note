package com.example.timemanager.presentation.screens.notes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.presentation.components.AppTextField
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.theme.AppTheme
import com.example.timemanager.presentation.util.Markdown
import com.example.timemanager.presentation.util.MarkdownEditing
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Экран заметки: просмотр отрендеренного markdown и режим редактирования
 * с панелью вставки меток. Новая заметка (id 0) открывается сразу в редакторе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    onBackClick: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val note = uiState.note
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Буферы редактирования: заполняются при входе в режим, чтобы отмена
    // («назад» из редактора) не портила сохранённый текст.
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(uiState.isEditing) {
        if (uiState.isEditing) {
            editTitle = note?.title.orEmpty()
            editContent = TextFieldValue(note?.content.orEmpty())
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = when {
                    uiState.isEditing -> stringResource(
                        if (viewModel.isNew) R.string.add_note else R.string.edit_note
                    )
                    else -> note?.title ?: stringResource(R.string.note_detail)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Из редактора существующей заметки «назад» —
                            // сначала выход в просмотр; новая просто закрывается.
                            if (uiState.isEditing && !viewModel.isNew) {
                                viewModel.stopEditing()
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(
                            enabled = editTitle.isNotBlank(),
                            onClick = {
                                viewModel.save(editTitle, editContent.text) {
                                    if (viewModel.isNew) onBackClick()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.save),
                                tint = AppTheme.colors.actionIcon
                            )
                        }
                    } else if (note != null) {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.edit_note)
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isEditing -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp)
            ) {
                AppTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = stringResource(R.string.note_title_label),
                    singleLine = true,
                    required = true,
                    // Как полей в настройках: светлая обводка и текст — на
                    // тёмном фоне экрана их стандартная палитра не читается.
                    onDarkBackground = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FormattingToolbar(
                    content = editContent,
                    onContentChange = { editContent = it }
                )
                AppTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    label = stringResource(R.string.note_content_label),
                    minLines = 10,
                    onDarkBackground = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            note != null -> Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (note.content.isNotBlank()) {
                    // Текст — на светлой «карточке» цвета диалогов: на тёмном
                    // фоне экрана длинные заметы читаются плохо.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(AppTheme.colors.dialogContainer)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = Markdown.render(note.content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.created_at, note.createdAt.formatDate()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            !uiState.isLoading -> Text(
                text = stringResource(R.string.note_not_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            )
        }
    }

    if (showDeleteDialog && note != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_note_confirm, note.title),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteNote(onDeleted = onBackClick)
            }
        )
    }
}

/**
 * Панель вставки markdown-меток. Операции — чистые функции
 * [MarkdownEditing] над текстом и выделением поля.
 */
@Composable
private fun FormattingToolbar(
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit
) {
    fun apply(op: (text: String, selection: TextRange) -> Pair<String, TextRange>) {
        val (text, selection) = op(content.text, content.selection)
        onContentChange(TextFieldValue(text, selection))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FormattingButton(R.drawable.ic_format_bold, R.string.note_format_bold) {
            apply { text, sel -> MarkdownEditing.toggleBold(text, sel) }
        }
        FormattingButton(R.drawable.ic_title, R.string.note_format_heading) {
            apply { text, sel -> MarkdownEditing.toggleLinePrefix(text, sel, "# ") }
        }
        FormattingButton(R.drawable.ic_format_list_bulleted, R.string.note_format_list) {
            apply { text, sel -> MarkdownEditing.toggleLinePrefix(text, sel, "- ") }
        }
        FormattingButton(R.drawable.ic_link, R.string.note_format_link) {
            apply { text, sel -> MarkdownEditing.insertLink(text, sel) }
        }
    }
}

@Composable
private fun FormattingButton(
    @DrawableRes iconRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            tint = AppTheme.colors.fieldOnDarkContent,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun Long.formatDate(): String {
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(this)
}
