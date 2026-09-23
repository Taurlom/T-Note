package com.example.timemanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Базовый диалог приложения: фон и форма из дизайн-системы, заголовок
 * единого стиля с крестиком закрытия в правом верхнем углу, контент —
 * скроллируемая колонка с едиными отступами.
 *
 * Крестик вызывает [onDismissRequest] — то же самое, что «Отмена» ранее:
 * диалог убирается из композиции, все введённые в нём значения (remember-состояние)
 * сбрасываются.
 */
@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    text: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = AppTheme.colors.dialogContainer,
        shape = MaterialTheme.shapes.small,
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    color = AppTheme.colors.dialogContent,
                    // Отступ справа, чтобы заголовок не залезал под крестик.
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(end = 40.dp)
                )
                IconButton(
                    onClick = onDismissRequest,
                    // 48-dp зона касания на 13 dp больше иконки с каждой стороны;
                    // сдвигаем так, чтобы правый и верхний края иконки встали
                    // точно на линии контента (линии паддинга диалога).
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 13.dp, y = (-13).dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.close),
                        tint = AppTheme.colors.dialogContent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        text = text?.let {
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = it
                )
            }
        },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}
