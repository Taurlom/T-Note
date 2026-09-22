package com.example.timemanager.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Флажок. Правило дизайн-системы: на тёмном фоне — золотой (secondary),
 * на светлом (диалоги) — синий (primary). [uncheckedColor] переопределяется
 * на светлых диалогах, где стандартный приглушённый цвет сливается с фоном.
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier,
    uncheckedColor: Color = AppTheme.colors.dialogContentMuted
) {
    val scheme = MaterialTheme.colorScheme
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = if (onDarkBackground) {
            CheckboxDefaults.colors(
                checkedColor = scheme.secondary,
                checkmarkColor = scheme.primary,
                uncheckedColor = scheme.secondary
            )
        } else {
            CheckboxDefaults.colors(
                checkedColor = scheme.primary,
                uncheckedColor = uncheckedColor
            )
        }
    )
}

/** Выборочный чип с иконкой — единый стиль для групп переключателей. */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = appChipColors()
    )
}

/** Палитра чипов дизайн-системы. */
@Composable
fun appChipColors(): SelectableChipColors {
    val colors = AppTheme.colors
    return FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.chipSelectedContainer,
        selectedLabelColor = colors.chipSelectedContent,
        selectedLeadingIconColor = colors.chipSelectedContent,
        containerColor = colors.chipContainer,
        labelColor = colors.chipContent,
        iconColor = colors.chipContent
    )
}
