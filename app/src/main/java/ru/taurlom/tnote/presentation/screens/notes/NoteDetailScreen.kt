package ru.taurlom.tnote.presentation.screens.notes

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.util.NoteImageRefs
import ru.taurlom.tnote.presentation.components.AppTextField
import ru.taurlom.tnote.presentation.components.AppTopBar
import ru.taurlom.tnote.presentation.components.ConfirmDeleteDialog
import ru.taurlom.tnote.presentation.components.PhotoGalleryDialog
import ru.taurlom.tnote.presentation.components.RemovablePhotoTile
import ru.taurlom.tnote.presentation.components.ZoomableImage
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.util.Markdown
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val MAX_NOTE_PHOTOS = 10

/**
 * Верхняя граница декодирования фото заметки в просмотре, px. Полный размер
 * снимков (4000×3000) — лишние десятки МБ памяти; экрану хватает ~ширины.
 */
private const val NOTE_IMAGE_MAX_PX = 1600

/**
 * Экран заметки. Просмотр: форматированный текст со встроенными изображениями
 * (read-only RichText, markdown-разметка не видна, текст выделяется и
 * копируется). Редактирование: WYSIWYG (compose-rich-editor), над выделением —
 * своя панель вместо системного меню (буфер обмена + форматирование, как в
 * Telegram). Изображения — ссылки `![](path)` прямо в тексте: в редакторе они
 * выглядят символом-заглушкой (compose-rich-editor не рендерит картинки
 * внутри поля ввода, трек #322), поэтому под полем показана лента миниатюр;
 * в просмотре рендерятся картинкой. Новая заметка (id 0) открывается сразу
 * в редакторе.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalRichTextApi::class)
@Composable
fun NoteDetailScreen(onBackClick: () -> Unit, viewModel: NoteDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val note = uiState.note
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var galleryIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }

    // Буферы редактирования заполняются при входе в режим, чтобы отмена
    // («назад» из редактора) не портила сохранённый текст. editorState сам
    // saveable (библиотека сериализует в HTML), остальные буферы — вручную:
    // isEditing живёт во ViewModel и переживает поворот, а обычный remember
    // затирал бы несохранённый ввод снапшотом из базы.
    val editorState = rememberRichTextState()
    var editTitle by rememberSaveable { mutableStateOf("") }
    // Uri — Parcelable, поэтому список тоже переживает пересоздание.
    val pendingPhotoUris = rememberSaveable { mutableStateListOf<Uri>() }
    var editBuffersLoaded by rememberSaveable { mutableStateOf(false) }
    // Выделение на момент открытия диалога ссылки: диалог забирает фокус,
    // и на части устройств выделение схлопывается — перед вставкой ссылки
    // диапазон восстанавливается.
    var linkSelection by remember { mutableStateOf(TextRange.Zero) }
    // Своя панель выделения вместо системного меню (см. NoteSelectionToolbar).
    val toolbarProvider = remember { NoteTextToolbarProvider() }
    DisposableEffect(toolbarProvider) { onDispose { toolbarProvider.close() } }
    // Индекс фото из ленты миниатюр редактора, открытое на предпросмотр.
    var editPreviewIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(uiState.isEditing) {
        if (uiState.isEditing && !editBuffersLoaded) {
            editTitle = note?.title.orEmpty()
            // isolateImageRefs: старые заметки могли сохранить картинку
            // склеенной с текстом абзаца — в редакторе такая строка ломает
            // позиционирование курсора, нормализуем до отдельных абзацев.
            setMarkdownWithImages(
                editorState,
                NoteImageRefs.isolateImageRefs(
                    NoteImageRefs.withAppendedMissing(note?.content.orEmpty(), note?.photoPaths.orEmpty()),
                ),
            )
            pendingPhotoUris.clear()
            editBuffersLoaded = true
        } else if (!uiState.isEditing) {
            editBuffersLoaded = false
        }
    }

    // Системный «назад»: из редактора существующей заметки — сначала выход
    // в просмотр без сохранения, из новой — закрытие экрана.
    BackHandler(enabled = uiState.isEditing) {
        if (viewModel.isNew) onBackClick() else viewModel.stopEditing()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_NOTE_PHOTOS),
    ) { uris ->
        val slots = MAX_NOTE_PHOTOS - NoteImageRefs.extractAll(editorState.toMarkdown()).size
        val accepted = uris.take(slots.coerceAtLeast(0))
        pendingPhotoUris += accepted
        // Каждое фото — отдельным абзацем после курсора: картинки не слипаются
        // в одну строку и удаляются атомарно в редакторе. Пустые <p> по краям —
        // якоря разбивки: без них insertParagraphs библиотеки вклеивает <img>
        // в конец текущего абзаца, картинка делит абзац с текстом, а тап по
        // такой строке ставит курсор в середину текста.
        editorState.insertHtmlAfterSelection(
            buildString {
                append("<p></p>")
                accepted.forEach { uri -> append("<p><img src=\"$uri\"></p>") }
                append("<p></p>")
            },
        )
    }

    fun save() {
        val markdown = editorState.toMarkdown()
        val referenced = NoteImageRefs.extractAll(markdown).toSet()
        viewModel.save(
            title = editTitle,
            content = markdown,
            // Отправляем в хранилище только те pending-URI, чьи ссылки остались
            // в тексте: удалённую в редакторе картинку копировать не нужно.
            newPhotoUris = pendingPhotoUris.filter { it.toString() in referenced },
        ) {
            if (viewModel.isNew) onBackClick()
        }
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
                        IconButton(enabled = editTitle.isNotBlank(), onClick = { save() }) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp)
                    .imePadding(),
            ) {
                // Добавление фото — иконкой «Add Photo Alternate» у заголовка,
                // а не плюсом в шапке: плюс читался как «добавить заметку»,
                // фото-иконка подсказывает действие прямо у места ввода.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = stringResource(R.string.note_title_label),
                        singleLine = true,
                        required = true,
                        onDarkBackground = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_photo),
                            contentDescription = stringResource(R.string.add_photo),
                            tint = AppTheme.colors.actionIcon,
                        )
                    }
                }
                // Подменяем системное меню выделения своей панелью: провайдер
                // перехватывает показ меню, панель рисуется оверлеем внутри
                // Box-якоря над границами выделения.
                var editorAnchor by remember { mutableStateOf<LayoutCoordinates?>(null) }
                Box(modifier = Modifier.onGloballyPositioned { editorAnchor = it }) {
                    CompositionLocalProvider(
                        LocalTextContextMenuToolbarProvider provides toolbarProvider,
                    ) {
                        OutlinedRichTextEditor(
                            state = editorState,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.note_content_label)) },
                            minLines = 10,
                            // Палитра как у AppTextField на фоне экрана (appTextFieldColorsOnDark).
                            colors = RichTextEditorDefaults.outlinedRichTextEditorColors(
                                textColor = AppTheme.colors.fieldOnDarkContent,
                                cursorColor = AppTheme.colors.fieldOnDarkContent,
                                focusedBorderColor = AppTheme.colors.fieldOnDarkContent,
                                unfocusedBorderColor = AppTheme.colors.fieldOnDarkBorder,
                                focusedLabelColor = AppTheme.colors.fieldOnDarkContent,
                                unfocusedLabelColor = AppTheme.colors.fieldOnDarkBorder,
                            ),
                        )
                    }
                    NoteSelectionToolbar(
                        provider = toolbarProvider,
                        state = editorState,
                        anchor = { editorAnchor },
                        onLinkClick = {
                            linkSelection = editorState.selection
                            showLinkDialog = true
                        },
                    )
                }
                // Лента миниатюр вложений: внутри поля картинка — лишь
                // символ-заглушка (compose-rich-editor #322), поэтому фото
                // видны здесь; крестик удаляет заглушку из текста.
                // Перезапуск только при изменении числа заглушек: сам
                // toMarkdown() — сериализация всего документа, вызывать его
                // на каждое нажатие клавиши нельзя (фризы на длинных
                // заметках). Счёт заглушек по annotatedString дёшев.
                val editorImageCount by remember {
                    derivedStateOf { editorState.annotatedString.text.count { it == '\uFFFD' } }
                }
                var editorImageRefs by remember { mutableStateOf(emptyList<String>()) }
                LaunchedEffect(editorImageCount) {
                    editorImageRefs = NoteImageRefs.extractAll(editorState.toMarkdown())
                }
                if (editorImageRefs.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        editorImageRefs.forEachIndexed { index, ref ->
                            RemovablePhotoTile(
                                model = noteImageModel(context, ref),
                                onRemove = { removeImageAt(editorState, index) },
                                modifier = Modifier.clickable { editPreviewIndex = index },
                            )
                        }
                    }
                }
            }

            note != null -> {
                // Старые заметки с галерейными фото: фото без ссылки в тексте
                // дописываются маркерами в конец, после первого сохранения
                // их позиции зафиксированы в тексте.
                val displayContent = NoteImageRefs.withAppendedMissing(note.content, note.photoPaths)
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    // Текст просмотра — прямо на основном фоне темы, без
                    // светлой «карточки»: шрифт — контрастный цвет темы
                    // (в тёмных темах светлый, в светлой — тёмный).
                    // Выделяется и копируется (SelectionContainer); тап по
                    // ссылке/фото — в их обработчики.
                    if (displayContent.isNotBlank()) {
                        SelectionContainer {
                            NoteRichText(
                                content = displayContent,
                                onImageClick = { galleryIndex = it },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.created_at, note.createdAt.formatDate()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
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

    if (showLinkDialog) {
        NoteLinkDialog(
            initialUrl = if (editorState.isLink) editorState.selectedLinkUrl.orEmpty() else "",
            onConfirm = { url ->
                if (editorState.selection.collapsed && !linkSelection.collapsed) {
                    editorState.selection = linkSelection
                }
                editorState.addLinkToSelection(url)
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false },
        )
    }

    galleryIndex?.let { index ->
        PhotoGalleryDialog(
            photoPaths = NoteImageRefs.extractAll(
                NoteImageRefs.withAppendedMissing(note?.content.orEmpty(), note?.photoPaths.orEmpty()),
            ),
            initialIndex = index,
            onDismiss = { galleryIndex = null },
        )
    }

    // Предпросмотр фото из ленты редактора: ссылки могут быть ещё не
    // сохранёнными content://, поэтому не PhotoGalleryDialog (он про файлы).
    editPreviewIndex?.let { index ->
        NoteImageRefs.extractAll(editorState.toMarkdown()).getOrNull(index)?.let { ref ->
            Dialog(
                onDismissRequest = { editPreviewIndex = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    ZoomableImage(
                        model = noteImageModel(context, ref),
                        contentDescription = stringResource(R.string.note_photo),
                        refreshKey = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = { editPreviewIndex = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
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

/**
 * Форматированный текст заметки в режиме просмотра: markdown рендерится
 * библиотекой (разметка не видна), изображения `![](path)` выводятся инлайн
 * через кастомный [ImageLoader] (локальные файлы из filesDir и content:// —
 * через Coil), тап по картинке открывает полноэкранную галерею.
 */
@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun NoteRichText(
    content: String,
    onImageClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    val imagePaths = remember(content) { NoteImageRefs.extractAll(content) }
    val imageLoader = remember(context, imagePaths) {
        object : ImageLoader {
            @Composable
            override fun load(model: Any): ImageData? {
                val path = model as? String ?: return null
                // Coil 2 без явного размера ждёт положительный размер холста,
                // а Image появится только после Success — взаимная блокировка,
                // загрузка никогда не стартует. Задаём размер в самом запросе
                // (с даунскейлом до разумного предела), тогда Coil грузит
                // сразу, без ожидания лайаута пейнтера.
                val request = remember(path) {
                    ImageRequest.Builder(context)
                        .data(noteImageModel(context, path))
                        .size(NOTE_IMAGE_MAX_PX)
                        .build()
                }
                val painter = rememberAsyncImagePainter(model = request)
                // Отдаём ImageData только после успешной загрузки — паттерн
                // официального Coil3-лоадера библиотеки: у success-пейнтера
                // уже известный размер, плейсхолдер сразу ресайзится под
                // картинку, без ожидания смены intrinsicSize у грузящегося.
                val success = painter.state as? AsyncImagePainter.State.Success ?: return null
                val index = imagePaths.indexOf(path)
                return ImageData(
                    painter = success.painter,
                    contentDescription = stringResource(R.string.note_photo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = index >= 0) { onImageClick(index) },
                )
            }
        }
    }
    // Ссылки — акцент темы, а не primary: у тёмных тем primary почти
    // сливается с основным фоном, на котором теперь лежит текст просмотра.
    val linkColor = AppTheme.colors.actionIcon
    val state = rememberRichTextState()
    LaunchedEffect(state, linkColor) { state.config.linkColor = linkColor }
    // Просмотр страдает от тех же багов markdown-парсера, что и редактор:
    // текст после подряд идущих картинок просто не попадает на экран.
    LaunchedEffect(content) { setMarkdownWithImages(state, content) }
    // Открываем только безопасные схемы: текст заметки потенциально недоверенный.
    val baseHandler = LocalUriHandler.current
    val uriHandler = remember(baseHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (Markdown.isOpenableLink(uri)) baseHandler.openUri(uri)
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        BasicRichText(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            imageLoader = imageLoader,
        )
    }
}

private fun Long.formatDate(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(this)

/**
 * Загрузка markdown с картинками в [RichTextState].
 *
 * Markdown-парсер библиотеки (compose-rich-editor 1.2.1) портит документы с
 * image-ссылками: съедает текст после подряд идущих картинок, оставляет
 * мусорные пробелы и сиротские заглушки U+FFFD, а созданные им image-спаны
 * ломаются при разрыве абзаца Enter'ом — спан дублируется на каждый
 * введённый рядом символ (класс багов #304/#661). Поэтому документ грузится
 * в два приёма: текст — markdown-парсером, но с токенами вместо ссылок,
 * картинки — по одной HTML-вставкой на место токена. Спаны этого пути
 * переживают правки, Enter и повторное сохранение (контракт —
 * NoteImagePipelineTest). Используется и редактором, и просмотром.
 */
internal fun setMarkdownWithImages(state: RichTextState, markdown: String) {
    val refs = NoteImageRefs.extractAll(markdown)
    if (refs.isEmpty()) {
        state.setMarkdown(markdown)
        return
    }
    // Токен из приватной зоны Юникода: в текстах заметок не встречается,
    // markdown-парсер пропускает его как обычный символ.
    val token = "\uE000"
    state.setMarkdown(NoteImageRefs.tokenizeImageRefs(markdown, token))
    var searchFrom = 0
    for (ref in refs) {
        val position = state.annotatedString.text.indexOf(token, searchFrom)
        if (position < 0) break
        // Единственный <img> внутри insertHtml вклеивается в абзац токена
        // ровно на позицию токена; после чего токен удаляется — картинка
        // занимает его место в тексте.
        state.insertHtml("<img src=\"$ref\">", position)
        val tokenPosition = state.annotatedString.text.indexOf(token, position)
        if (tokenPosition >= 0) {
            state.removeTextRange(TextRange(tokenPosition, tokenPosition + token.length))
        }
        searchFrom = position + 1
    }
}

/** Модель Coil для ссылки из текста заметки: content:// как есть, остальное — файл из filesDir. */
private fun noteImageModel(context: Context, ref: String): Any =
    if (ref.startsWith("content://")) ref.toUri() else File(context.filesDir, ref)

/**
 * Удаление i-й картинки из текста редактора. Image-спан владеет ровно одним
 * символом U+FFFD (контракт проверен в NoteImagePipelineTest), а порядок
 * заглушек в тексте совпадает с порядком ссылок в toMarkdown — оба идут
 * по документу. richParagraphList у библиотеки internal, поэтому позицию
 * находим сканированием текста, а не обходом дерева спанов.
 */
private fun removeImageAt(state: RichTextState, imageIndex: Int) {
    var count = 0
    val text = state.annotatedString.text
    val position = text.indexOfFirst {
        if (it == '\uFFFD') {
            if (count == imageIndex) return@indexOfFirst true
            count++
        }
        false
    }
    if (position < 0) return
    // Заглушка занимает отдельный абзац, поэтому вместе с ней убираем и
    // один из разделителей абзацев (в плоском тексте это пробел), чтобы
    // не оставлять пустую строку на месте удалённой картинки.
    val start = if (position > 0 && text[position - 1] == ' ') position - 1 else position
    val end = if (start == position && position + 1 < text.length && text[position + 1] == ' ') {
        position + 2
    } else {
        position + 1
    }
    state.removeTextRange(TextRange(start, end))
}
