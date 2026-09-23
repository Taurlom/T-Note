package com.example.timemanager.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Единый выпадающий список приложения (роль «select»): нередактируемое поле
 * [AppTextField] с лейблом и стрелкой + меню той же ширины, что поле.
 *
 * Используется и в светлых диалогах, и на тёмном фоне экранов — см.
 * [onDarkBackground]. Текст варианта полностью на стороне вызывающего
 * ([optionText]), чтобы можно было задать свой стиль (например, каждый
 * пункт списка шрифтов — своим шрифтом). Иконку варианта добавляет
 * [optionIcon].
 *
 * @param selectedLabel текущий выбор; `null`/пустая строка показывает [placeholder].
 * @param required звёздочка обязательного поля в лейбле (см. [AppTextField]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppDropdown(
    label: String,
    selectedLabel: String?,
    options: List<T>,
    optionText: @Composable (T) -> Unit,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    optionIcon: (@Composable (T) -> Unit)? = null,
    required: Boolean = false,
    onDarkBackground: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    // Ширина выпадающего меню = ширина поля-якоря (в Material 3.1.3 нет
    // matchDropDownWidthToComponent, измеряем сами).
    var anchorWidth by remember { mutableIntStateOf(0) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        AppTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = { },
            label = label,
            placeholder = placeholder,
            singleLine = true,
            readOnly = true,
            required = required,
            onDarkBackground = onDarkBackground,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .onSizeChanged { anchorWidth = it.width },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AppTheme.colors.dialogContainer,
            modifier = with(LocalDensity.current) {
                if (anchorWidth > 0) Modifier.width(anchorWidth.toDp()) else Modifier
            }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { optionText(option) },
                    leadingIcon = optionIcon?.let { iconContent ->
                        { iconContent(option) }
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
