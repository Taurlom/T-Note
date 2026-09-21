package com.example.timemanager.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.presentation.theme.DialogContainer
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.OnSurfaceVariant
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.Primary
import com.example.timemanager.presentation.theme.PrimaryButtonContainer
import com.example.timemanager.presentation.theme.Secondary

/**
 * Флажок. Правило дизайн-системы: на тёмном фоне — золотой (Secondary),
 * на светлом (диалоги) — синий (Primary).
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = if (onDarkBackground) {
            CheckboxDefaults.colors(
                checkedColor = Secondary,
                checkmarkColor = Primary,
                uncheckedColor = Secondary
            )
        } else {
            CheckboxDefaults.colors(
                checkedColor = PrimaryButtonContainer,
                uncheckedColor = OnSurfaceVariant
            )
        }
    )
}

/** Переключатель (будильник и т.п.) для светлых диалогов. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = PrimaryButtonContainer,
            checkedThumbColor = OnPrimary,
            uncheckedTrackColor = OnSurfaceVariant.copy(alpha = 0.4f),
            uncheckedThumbColor = DialogContainer,
            uncheckedBorderColor = OnSurfaceVariant
        )
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
fun appChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PrimaryButtonContainer,
    selectedLabelColor = OnPrimary,
    selectedLeadingIconColor = OnPrimary,
    containerColor = OnTertiary.copy(alpha = 0.08f),
    labelColor = OnTertiary,
    iconColor = OnTertiary
)
