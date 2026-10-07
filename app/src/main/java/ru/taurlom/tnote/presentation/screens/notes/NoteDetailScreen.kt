package ru.taurlom.tnote.presentation.screens.notes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.components.AppTextField
import ru.taurlom.tnote.presentation.components.AppTopBar
import ru.taurlom.tnote.presentation.components.ConfirmDeleteDialog
import ru.taurlom.tnote.presentation.components.PhotoGalleryDialog
import ru.taurlom.tnote.presentation.components.PhotoTile
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.util.Markdown
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val MAX_NOTE_PHOTOS = 10

/**
 * Экран заметки: просмотр отрендеренного markdown с сеткой прикреплённых
 * фото и режим редактирования с панелью вставки меток. Новая заметка (id 0)
 * открывается сразу в редакторе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(onBackClick: () -> Unit, viewModel: NoteDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val note = uiState.note
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var galleryIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }

    // Буферы редактирования: заполняются при входе в режим, чтобы отмена
    // («назад» из редактора) не портила сохранённый текст.
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf(TextFieldValue("")) }
    val editPhotoPaths = remember { mutableStateListOf<String>() }
    val pendingPhotoUris = remember { mutableStateListOf<Uri>() }
    val removedPhotoPaths = remember { mutableStateListOf<String>() }
    LaunchedEffect(uiState.isEditing) {
        if (uiState.isEditing) {
            editTitle = note?.title.orEmpty()
            editContent = TextFieldValue(note?.content.orEmpty())
            editPhotoPaths.clear()
            editPhotoPaths += note?.photoPaths.orEmpty()
            pendingPhotoUris.clear()
            removedPhotoPaths.clear()
        }
    }

    // Системный «назад»: из редактора существующей заметки — сначала выход
    // в просмотр, из новой — закрытие экрана.
    BackHandler(enabled = uiState.isEditing) {
        if (viewModel.isNew) onBackClick() else viewModel.stopEditing()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_NOTE_PHOTOS),
    ) { uris ->
        val slots = MAX_NOTE_PHOTOS -
            (editPhotoPaths.size + pendingPhotoUris.size - removedPhotoPaths.size)
        pendingPhotoUris += uris.take(slots.coerceAtLeast(0))
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = when {
                    uiState.isEditing -> stringResource(
                        if (viewModel.isNew) R.string.add_note else R.string.edit_note,
                    )
                    else -> note?.title ?: stringResource(R.string.note_detail)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isEditing && !viewModel.isNew) {
                                viewModel.stopEditing()
                            } else {
                                onBackClick()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(
                            enabled = editTitle.isNotBlank(),
                            onClick = {
                                viewModel.save(
                                    title = editTitle,
                                    content = editContent.text,
                                    newPhotoUris = pendingPhotoUris.toList(),
                                    removedPhotoPaths = removedPhotoPaths.toList(),
                                ) {
                                    if (viewModel.isNew) onBackClick()
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.save),
                                tint = AppTheme.colors.actionIcon,
                            )
                        }
                    } else if (note != null) {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit),
                                contentDescription = stringResource(R.string.edit_note),
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isEditing -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Поля скроллятся, панель форматирования — прижата вниз над
                // клавиатурой: системный тулбар выделения всплывает НАД
                // выделенным текстом и, если панель стоит над полем,
                // перекрывает её кнопки при выделении первой строки.
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    AppTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = stringResource(R.string.note_title_label),
                        singleLine = true,
                        required = true,
                        onDarkBackground = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = stringResource(R.string.note_content_label),
                        minLines = 10,
                        onDarkBackground = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        editPhotoPaths.forEach { path ->
                            NotePhotoThumb(
                                model = File(context.filesDir, path).toUri(),
                                version = photoVersion,
                                onRemove = {
                                    editPhotoPaths -= path
                                    removedPhotoPaths += path
                                },
                            )
                        }
                        pendingPhotoUris.forEach { uri ->
                            NotePhotoThumb(
                                model = uri,
                                onRemove = { pendingPhotoUris -= uri },
                            )
                        }
                        if (editPhotoPaths.size + pendingPhotoUris.size < MAX_NOTE_PHOTOS) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = stringResource(R.string.add_photo),
                                    tint = AppTheme.colors.fieldOnDarkContent,
                                )
                            }
                        }
                    }
                }
                FormattingToolbar(
                    content = editContent,
                    onContentChange = { editContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            note != null -> Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
            ) {
                if (note.content.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(AppTheme.colors.dialogContainer)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = Markdown.render(note.content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (note.photoPaths.isNotEmpty()) {
                    note.photoPaths.chunked(2).forEachIndexed { rowIndex, rowPaths ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowPaths.forEachIndexed { colIndex, path ->
                                val index = rowIndex * 2 + colIndex
                                PhotoTile(
                                    model = File(context.filesDir, path).toUri(),
                                    version = photoVersion,
                                    onClick = { galleryIndex = index },
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                )
                            }
                            if (rowPaths.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.created_at, note.createdAt.formatDate()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            !uiState.isLoading -> Text(
                text = stringResource(R.string.note_not_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            )
        }
    }

    galleryIndex?.let { index ->
        PhotoGalleryDialog(
            photoPaths = note?.photoPaths ?: emptyList(),
            initialIndex = index,
            onDismiss = {
                galleryIndex = null
                photoVersion++
            },
        )
    }

    if (showDeleteDialog && note != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_note_confirm, note.title),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteNote(onDeleted = onBackClick)
            },
        )
    }
}

private fun Long.formatDate(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(this)
