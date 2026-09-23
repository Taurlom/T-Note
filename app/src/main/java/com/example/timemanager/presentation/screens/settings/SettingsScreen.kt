package com.example.timemanager.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.presentation.components.AppDropdown
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.AppTheme
import com.example.timemanager.presentation.theme.ThemeKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Нижний бар лежит под пейджером в MainTabsScreen — его не учитываем.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AppTopBar(title = stringResource(R.string.settings_title))
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.font_label),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.sectionTitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Шрифт, как и тема, применяется сразу при выборе.
            // Каждый пункт списка показан своим шрифтом.
            AppDropdown(
                label = stringResource(R.string.font_label),
                selectedLabel = uiState.selectedFont.displayName,
                options = AppFont.entries.toList(),
                optionText = { font ->
                    Text(
                        text = font.displayName,
                        fontFamily = font.fontFamily,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.dialogContent
                    )
                },
                onSelect = { viewModel.applyFont(it) },
                onDarkBackground = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.theme_label),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.sectionTitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Тема применяется сразу при выборе — так видно, что она делает.
            AppDropdown(
                label = stringResource(R.string.theme_label),
                selectedLabel = uiState.selectedTheme.displayName,
                options = ThemeKind.entries.toList(),
                optionText = { theme ->
                    Text(
                        text = theme.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.dialogContent
                    )
                },
                onSelect = { viewModel.applyTheme(it) },
                onDarkBackground = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            AppTextButton(
                onClick = { showClearDialog = true },
                textRes = R.string.clear_calendar,
                modifier = Modifier.fillMaxWidth()
            )

            if (showClearDialog) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.clear_calendar),
                    text = stringResource(R.string.clear_calendar_message),
                    onDismiss = { showClearDialog = false },
                    onConfirm = {
                        viewModel.clearCalendar()
                        showClearDialog = false
                    }
                )
            }
        }
    }
}

