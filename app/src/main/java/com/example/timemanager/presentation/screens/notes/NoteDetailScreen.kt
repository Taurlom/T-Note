package com.example.timemanager.presentation.screens.notes

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.presentation.components.AppTextField
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.PhotoGalleryDialog
import com.example.timemanager.presentation.components.PhotoTile
import com.example.timemanager.presentation.theme.AppTheme
import com.example.timemanager.presentation.util.Markdown
import com.example.timemanager.presentation.util.MarkdownEditing
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
fun NoteDetailScreen(
    onBackClick: () -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val note = uiState.note
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var galleryIndex by remember { mutableStateOf<Int?>(null) }
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

    // Системный «назад» идёт тем же маршрутом, что стрелка тулбара:
    // из редактора существующей заметки — сначала выход в просмотр,
    // из новой — закрытие экрана. Без перехвата жест «назад» выкидывал
    // экран целиком и молча терял набранный текст (черновик живёт
    // в remember и не переживает ухода экрана из композиции).
    // В просмотре перехват выключен — «назад» закрывает экран как обычно.
    BackHandler(enabled = uiState.isEditing) {
        if (viewModel.isNew) onBackClick() else viewModel.stopEditing()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_NOTE_PHOTOS)
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
                                viewModel.save(
                                    title = editTitle,
                                    content = editContent.text,
                                    newPhotoUris = pendingPhotoUris.toList(),
                                    removedPhotoPaths = removedPhotoPaths.toList()
                                ) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                    AppTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = stringResource(R.string.note_content_label),
                        minLines = 10,
                        onDarkBackground = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        editPhotoPaths.forEach { path ->
                            NotePhotoThumb(
                                model = File(context.filesDir, path).toUri(),
                                version = photoVersion,
                                onRemove = {
                                    editPhotoPaths -= path
                                    removedPhotoPaths += path
                                }
                            )
                        }
                        pendingPhotoUris.forEach { uri ->
                            NotePhotoThumb(
                                model = uri,
                                onRemove = { pendingPhotoUris -= uri }
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
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = stringResource(R.string.add_photo),
                                    tint = AppTheme.colors.fieldOnDarkContent
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
                        .padding(horizontal = 8.dp, vertical = 6.dp)
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
                // Название — только в шапке: повтор в теле — дубль, а внутри
                // заметки автор сам пишет заголовок, когда он нужен.
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

                if (note.photoPaths.isNotEmpty()) {
                    // Сетка как у документов: ряды по два, одиночное фото
                    // последней строки остаётся половинной ширины.
                    note.photoPaths.chunked(2).forEachIndexed { rowIndex, rowPaths ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowPaths.forEachIndexed { colIndex, path ->
                                val index = rowIndex * 2 + colIndex
                                PhotoTile(
                                    model = File(context.filesDir, path).toUri(),
                                    version = photoVersion,
                                    onClick = { galleryIndex = index },
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
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

    galleryIndex?.let { index ->
        PhotoGalleryDialog(
            photoPaths = note?.photoPaths ?: emptyList(),
            initialIndex = index,
            onDismiss = {
                galleryIndex = null
                photoVersion++
            }
            // onCropComplete не передаём: кроп в галерее скрыт, он умеет
            // переоформлять пути только у документов.
        )
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

/** Миниатюра фото заметки с крестиком удаления из черновика. */
@Composable
private fun NotePhotoThumb(
    model: Any,
    onRemove: () -> Unit,
    version: Any? = null
) {
    Box(modifier = Modifier.size(84.dp)) {
        PhotoTile(
            model = model,
            version = version,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.delete),
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Панель вставки markdown-меток. Операции — чистые функции
 * [MarkdownEditing] над текстом и выделением поля.
 */
@Composable
private fun FormattingToolbar(
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    // Подпись пустой ссылки локализуема — читаем ресурс здесь, а не в onClick.
    val linkPlaceholder = stringResource(R.string.markdown_link_placeholder)

    fun apply(op: (text: String, selection: TextRange) -> Pair<String, TextRange>) {
        val (text, selection) = op(content.text, content.selection)
        onContentChange(TextFieldValue(text, selection))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
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
            apply { text, sel -> MarkdownEditing.insertLink(text, sel, linkPlaceholder) }
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
