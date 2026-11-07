package ru.taurlom.tnote.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/**
 * Состояние режима поиска: флаг активности, текст запроса и флаг «саджесты
 * спрятаны». Сохраняется через [Saver] — поворот экрана не сбрасывает ни
 * открытый поиск, ни введённый запрос (флаг затеняющих подсказок не
 * сохраняется — после поворота меню снова доступно).
 */
class SearchState(active: Boolean = false, query: String = "") {
    var active by mutableStateOf(active)
        private set
    var query by mutableStateOf(query)
        private set

    /** Тап/скролл мимо меню подсказок прячет его до следующего изменения запроса. */
    var suggestionsDismissed by mutableStateOf(false)
        private set

    fun open() {
        active = true
    }

    /** Закрытие сбрасывает запрос: следующий поиск начинается с чистого поля. */
    fun close() {
        active = false
        query = ""
        suggestionsDismissed = false
    }

    fun onQueryChange(value: String) {
        query = value
        // Продолжение ввода снова показывает спрятанное меню.
        suggestionsDismissed = false
    }

    /** Спрятать меню подсказок (вызывается при жесте по контенту экрана). */
    fun dismissSuggestions() {
        suggestionsDismissed = true
    }

    companion object {
        val Saver: Saver<SearchState, Any> = listSaver(
            save = { listOf(it.active, it.query) },
            restore = { SearchState(active = it[0] as Boolean, query = it[1] as String) },
        )
    }
}

@Composable
fun rememberSearchState(): SearchState = rememberSaveable(saver = SearchState.Saver) { SearchState() }

/**
 * Подходит ли элемент под поисковый запрос: регистронезависимый поиск
 * подстроки по любому из переданных полей. Пустой запрос матчит всё —
 * фильтр можно не ветвить снаружи.
 */
fun searchMatch(query: String, vararg fields: String?): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return fields.any { it != null && it.contains(q, ignoreCase = true) }
}

/**
 * Шапка режима поиска: заменяет обычную шапку раздела, пока поиск открыт.
 * Кнопка ✕ закрывает режим (заодно системная «Назад» — см. [BackHandler]),
 * поле ввода получает фокус сразу, IME-кнопка «Найти» просто убирает
 * клавиатуру — фильтрация идёт на лету по мере ввода. Программная замена
 * запроса (выбор саджеста) ставит курсор в конец текста.
 *
 * [suggestions] — варианты автокомплита под полем (например, теги для
 * запроса вида «#пре»); пустой список — меню не показывается. Выбор
 * варианта отдаётся вызывающему ([onSuggestionClick]) — он решает, чем
 * заполнить запрос. Меню нефокусируемое: появление и выбор варианта не
 * уводят фокус с поля ввода, клавиатура не сворачивается (см.
 * [SearchSuggestionsMenu]). Спрятать меню до следующего изменения запроса
 * можно через [suggestionsDismissed] / [onDismissSuggestions] — экран
 * делает это жестом по контенту (саджесты есть только в заметках).
 *
 * Общий компонент для заметок, документов и списков задач.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit = {},
    suggestionsDismissed: Boolean = false,
    onDismissSuggestions: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Системная «Назад» закрывает поиск вместо выхода с экрана.
    BackHandler(onBack = onClose)

    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.close),
                )
            }
        },
        title = {
            // Ширина поля — саджесты раскрываются на всю длину строки поиска.
            var anchorWidthPx by remember { mutableStateOf(0) }
            // Поле хранит TextFieldValue, а не String: при внешнем изменении
            // запроса (выбор саджеста, закрытие поиска) нужно ставить курсор
            // в конец — иначе он остаётся на старой позиции (после «#ко» в
            // «#коктейль»), и правку приходится начинать с тапа.
            var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                mutableStateOf(TextFieldValue(query, TextRange(query.length)))
            }
            // Основанное на параметре значение: пока запрос сбрасывается извне,
            // текст не совпадает — подставляем свежий с курсором в конце.
            // При обычном вводе текст совпадает, и своё выделение (в т.ч.
            // после ввода/удаления букв) поле не теряет.
            val displayed = if (fieldValue.text != query) {
                TextFieldValue(query, TextRange(query.length))
            } else {
                fieldValue
            }
            Box(modifier = Modifier.onSizeChanged { anchorWidthPx = it.width }) {
                TextField(
                    value = displayed,
                    onValueChange = { value ->
                        fieldValue = value
                        onQueryChange(value.text)
                    },
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = AppTheme.colors.appBarContent,
                        unfocusedTextColor = AppTheme.colors.appBarContent,
                        cursorColor = AppTheme.colors.appBarContent,
                        focusedPlaceholderColor = AppTheme.colors.appBarContent.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = AppTheme.colors.appBarContent.copy(alpha = 0.6f),
                    ),
                )
                SearchSuggestionsMenu(
                    suggestions = suggestions,
                    onSuggestionClick = onSuggestionClick,
                    dismissed = suggestionsDismissed,
                    onDismiss = onDismissSuggestions,
                    anchorWidth = with(LocalDensity.current) { anchorWidthPx.toDp() },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppTheme.colors.appBarContainer,
            scrolledContainerColor = AppTheme.colors.appBarScrolledContainer,
            navigationIconContentColor = AppTheme.colors.appBarContent,
        ),
    )

    // Автофокус: поиск открылся — можно печатать сразу, без тапа по полю.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * Выпадающие подсказки автокомплита под полем поиска. Ширина — как у поля
 * поиска ([anchorWidth]); фон — роль surface: в тёмных темах она тёмная
 * (у части тёмных тем surfaceContainer светлый — меню по умолчанию вышло
 * бы светлым), в светлой — белая.
 *
 * Вместо DropdownMenu (material3) — явный нефокусируемый [Popup]:
 * фокусируемое окно забирает фокус у поля поиска каждый раз при появлении
 * (первое совпадение, ввод и удаление символов), IME сворачивается и для
 * продолжения набора нужны два тапа. Нефокусируемый popup фокус не
 * трогает — набор идёт непрерывно, тап по варианту работает как обычный
 * клик. Обратная сторона: нефокусируемое окно не умеет ловить тапы мимо
 * себя, поэтому «тап мимо прячет меню» реализуется вызывающим экраном —
 * жест по контенту вызывает [onDismiss] (см. NotesScreen).
 */
@Composable
private fun SearchSuggestionsMenu(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    dismissed: Boolean,
    onDismiss: () -> Unit,
    anchorWidth: Dp,
) {
    if (dismissed || suggestions.isEmpty()) return

    // Позиция: под якорем (левый край совпадает с полем); если меню не
    // влезает до низа экрана — прижать к нижней границе.
    val positionProvider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val y = minOf(
                    anchorBounds.bottom,
                    windowSize.height - popupContentSize.height,
                ).coerceAtLeast(0)
                return IntOffset(anchorBounds.left, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        // Только системное «Назад» могло бы дёргать колбэк у фокусируемого
        // окна; здесь нефокусируемое — ничего не вызывает, прятание по
        // [onDismiss].
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.width(anchorWidth)) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionClick(suggestion) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#$suggestion",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ──

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Пустой запрос")
@Composable
private fun SearchTopBarEmptyPreview() {
    TNoteTheme {
        SearchTopBar(
            query = "",
            onQueryChange = {},
            onClose = {},
            placeholder = "Поиск заметок",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "С запросом")
@Composable
private fun SearchTopBarWithQueryPreview() {
    TNoteTheme {
        SearchTopBar(
            query = "рецепт",
            onQueryChange = {},
            onClose = {},
            placeholder = "Поиск заметок",
        )
    }
}
