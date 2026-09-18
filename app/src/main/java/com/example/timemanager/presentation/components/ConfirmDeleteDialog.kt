package com.example.timemanager.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.DialogBackground
import com.example.timemanager.presentation.theme.DialogButtonBackground
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.OnTertiary

@Composable
fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        title = { Text(title, color = OnTertiary) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = OnTertiary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary,
                    disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                    disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary,
                    disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                    disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                )
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(3.dp)
    )
}
