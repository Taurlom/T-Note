package ru.taurlom.tnote.presentation.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/**
 * Пустое состояние раздела: крупная приглушённая иконка раздела и пояснение.
 *
 * Показывается по центру экрана вместо голого текста, чтобы пользователь
 * сразу видел, куда попал, и понимал, что делать дальше. Цвет —
 * [AppTheme.colors.dialogContentMuted]: состояние вторично по отношению
 * к контенту и не должно кричать.
 */
@Composable
fun EmptyState(@DrawableRes iconRes: Int, @StringRes messageRes: Int, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = AppTheme.colors.dialogContentMuted,
            modifier = Modifier.size(80.dp),
        )
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = AppTheme.colors.dialogContentMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Previews — пустое состояние в обеих темах
// ═══════════════════════════════════════════════════════════════════════

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    TNoteTheme {
        EmptyState(
            iconRes = R.drawable.ic_list_alt,
            messageRes = R.string.no_categories,
            modifier = Modifier.size(width = 320.dp, height = 480.dp),
        )
    }
}
