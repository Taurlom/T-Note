package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/**
 * Стандартное текстовое поле приложения (светлая тема диалогов и экранов).
 *
 * Все цвета — из ролей дизайн-системы, чтобы не дублировать
 * `OutlinedTextFieldDefaults.colors(...)` в каждом экране.
 *
 * Необязательные пропсы:
 *  - [required] — добавляет в лейбл звёздочку цвета [AppColors.requiredMarker],
 *    показывая, что поле нужно заполнить;
 *  - [onDarkBackground] — палитра для полей на тёмном фоне экрана
 *    (настройки, карточки), а не в светлом диалоге.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
    required: Boolean = false,
    onDarkBackground: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppFieldLabel(label, required) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        modifier = modifier,
        colors = if (onDarkBackground) appTextFieldColorsOnDark() else appTextFieldColors(),
    )
}

/**
 * Тот же поле, но с полным [TextFieldValue]: позиция курсора и выделение
 * живут в состоянии вызывающего — нужно редактору заметок, где панель
 * форматирования вставляет метки у выделения.
 */
@Composable
fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    required: Boolean = false,
    onDarkBackground: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { AppFieldLabel(label, required) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        modifier = modifier,
        colors = if (onDarkBackground) appTextFieldColorsOnDark() else appTextFieldColors(),
    )
}

/** Лейбл поля: у обязательных — звёздочка цвета [AppColors.requiredMarker]. */
@Composable
private fun AppFieldLabel(label: String, required: Boolean) {
    Text(
        text = if (required) {
            buildAnnotatedString {
                append(label)
                withStyle(SpanStyle(color = AppTheme.colors.requiredMarker)) {
                    append(" *")
                }
            }
        } else {
            AnnotatedString(label)
        },
    )
}

/** Палитра полей на тёмном фоне экрана (например, выпадающий список шрифта). */
@Composable
fun appTextFieldColorsOnDark(): TextFieldColors {
    val colors = AppTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.fieldOnDarkContent,
        unfocusedTextColor = colors.fieldOnDarkContent,
        disabledTextColor = colors.fieldOnDarkContent,
        cursorColor = colors.fieldOnDarkContent,
        focusedBorderColor = colors.fieldOnDarkContent,
        unfocusedBorderColor = colors.fieldOnDarkBorder,
        focusedLabelColor = colors.fieldOnDarkContent,
        unfocusedLabelColor = colors.fieldOnDarkBorder,
        focusedTrailingIconColor = colors.fieldOnDarkContent,
        unfocusedTrailingIconColor = colors.fieldOnDarkContent,
    )
}

/** Палитра текстовых полей — единая для всех экранов. */
@Composable
fun appTextFieldColors(): TextFieldColors {
    val colors = AppTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.fieldContent,
        unfocusedTextColor = colors.fieldContent,
        disabledTextColor = colors.fieldContent,
        cursorColor = colors.fieldContent,
        focusedBorderColor = colors.fieldBorder,
        unfocusedBorderColor = colors.fieldBorderUnfocused,
        focusedLabelColor = colors.fieldBorder,
        unfocusedLabelColor = colors.fieldBorderUnfocused,
        disabledBorderColor = colors.fieldBorderUnfocused,
        disabledLabelColor = colors.fieldBorder,
        disabledTrailingIconColor = colors.dialogContentMuted,
        disabledPlaceholderColor = colors.dialogContentMuted,
        focusedTrailingIconColor = colors.fieldContent,
        unfocusedTrailingIconColor = colors.fieldContent,
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Previews — поля на тёмном и светлом фоне, пустые и заполненные
// ═══════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, name = "Светлый фон — пустое")
@Composable
private fun AppTextFieldLightEmptyPreview() {
    TNoteTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppTextField(
                value = "",
                onValueChange = {},
                label = "Название",
                placeholder = "Введите название",
                required = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, name = "Тёмный фон — заполненное")
@Composable
private fun AppTextFieldDarkFilledPreview() {
    TNoteTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppTextField(
                value = "Пример текста заметки",
                onValueChange = {},
                label = "Содержание",
                minLines = 3,
                onDarkBackground = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
