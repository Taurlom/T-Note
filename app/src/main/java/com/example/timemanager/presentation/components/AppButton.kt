package com.example.timemanager.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Основная filled-кнопка — единственный источник стиля для всех кнопок приложения.
 *
 * Форма берётся из [MaterialTheme.shapes] (бренд: угол 3.dp), палитра — из
 * ролей дизайн-системы.
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.buttonContainer,
            contentColor = AppTheme.colors.buttonContent,
            disabledContainerColor = AppTheme.colors.buttonDisabledContainer,
            disabledContentColor = AppTheme.colors.buttonDisabledContent
        ),
        content = content
    )
}

/** Кнопка «Сохранить» — самая частая в диалогах. */
@Composable
fun AppSaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AppButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(stringResource(R.string.save))
    }
}

/** Кнопка «Отмена». */
@Composable
fun AppCancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.cancel))
    }
}

/** Кнопка с текстовым id ресурса (например «Удалить», «Применить»). */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    @StringRes textRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AppButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(stringResource(textRes))
    }
}

/** Контурная кнопка на светлом фоне диалога. */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.dialogContent),
        content = content
    )
}

/** Плавающая кнопка «Добавить» — единый стиль для всех разделов. */
@Composable
fun AppFab(
    onClick: () -> Unit,
    @StringRes contentDescriptionRes: Int,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        containerColor = AppTheme.colors.fabContainer,
        contentColor = AppTheme.colors.fabContent
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = stringResource(contentDescriptionRes),
            modifier = Modifier.size(28.dp)
        )
    }
}
