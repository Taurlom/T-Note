package ru.taurlom.tnote.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/**
 * Состояние режима поиска: флаг активности и текст запроса.
 * Сохраняется через [Saver] — поворот экрана не сбрасывает ни открытый
 * поиск, ни введённый запрос.
 */
class SearchState(active: Boolean = false, query: String = "") {
    var active by mutableStateOf(active)
        private set
    var query by mutableStateOf(query)
        private set

    fun open() {
        active = true
    }

    /** Закрытие сбрасывает запрос: следующий поиск начинается с чистого поля. */
    fun close() {
        active = false
        query = ""
    }

    fun onQueryChange(value: String) {
        query = value
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
 * клавиатуру — фильтрация идёт на лету по мере ввода.
 *
 * Общий компонент для заметок, документов и списков задач.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, placeholder: String, modifier: Modifier = Modifier) {
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
            TextField(
                value = query,
                onValueChange = onQueryChange,
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
