package ru.taurlom.tnote.presentation.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.components.AppButton

/**
 * Заглушка защищённого раздела «Документы»: вместо содержимого — замок
 * и кнопка «Разблокировать». Системное подтверждение запускается ТОЛЬКО
 * по кнопке: авто-запрос при проявлении заглушки мешал пролистать раздел
 * дальше, не заходя в него.
 */
@Composable
fun DocumentsLockOverlay(onUnlocked: () -> Unit) {
    val startVerification = rememberDeviceCredentialVerifier(
        title = stringResource(R.string.documents_lock_prompt_title),
        onResult = { confirmed -> if (confirmed) onUnlocked() },
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.documents_locked_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(24.dp))
            AppButton(onClick = startVerification) {
                Text(stringResource(R.string.documents_locked_unlock))
            }
        }
    }
}
