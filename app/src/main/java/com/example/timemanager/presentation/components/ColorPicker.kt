package com.example.timemanager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/** Пресеты цветов категорий. */
private val presetColors = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
    0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1, 0xFF00897B,
    0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFFFDD835,
    0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41,
    0xFF757575, 0xFF546E7A
)

/** Цвет нового списка, пока пользователь ничего не выбрал. */
fun defaultCategoryColor(): Long = presetColors.first()

/** Палитра категорий в формате общего [AppColorPicker]. */
fun categoryColorOptions(): List<ColorSwatchOption> =
    presetColors.map { ColorSwatchOption(it, Color(it)) }

/**
 * Один кружок палитры: значение (то, что хранится в базе), отображаемый
 * цвет и необязательная label для доступности/подписи.
 */
data class ColorSwatchOption(
    val value: Long,
    val swatch: Color,
    val label: String? = null
)

/**
 * Палитра выбора цвета: круглые плашки переносом строк — все цвета видны
 * сразу (в отличие от прежнего скроллящегося ряда).
 *
 * @param size диаметр плашки: 40.dp в диалоге категории, 28.dp в редакторе
 *        события.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppColorPicker(
    options: List<ColorSwatchOption>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        options.forEach { option ->
            ColorSwatch(
                swatch = option.swatch,
                selected = option.value == selected,
                label = option.label,
                size = size,
                onClick = { onSelect(option.value) }
            )
        }
    }
}

/** Кружок-переключатель цвета: рамка и галочка при выборе. */
@Composable
private fun ColorSwatch(
    swatch: Color,
    selected: Boolean,
    label: String?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) AppTheme.colors.dialogContent else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClickLabel = label) { onClick() }
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}
