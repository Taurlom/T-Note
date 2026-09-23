package com.example.timemanager.presentation.components

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
import androidx.compose.ui.text.withStyle
import com.example.timemanager.presentation.theme.AppTheme

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
    onDarkBackground: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
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
                }
            )
        },
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
        colors = if (onDarkBackground) appTextFieldColorsOnDark() else appTextFieldColors()
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
        unfocusedTrailingIconColor = colors.fieldOnDarkContent
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
        unfocusedTrailingIconColor = colors.fieldContent
    )
}
