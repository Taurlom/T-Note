package com.example.timemanager.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.appTextFieldColorsOnDark
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.Outline
import com.example.timemanager.presentation.theme.Primary
import com.example.timemanager.presentation.theme.Secondary
import com.example.timemanager.presentation.theme.Tertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingFont by remember { mutableStateOf(uiState.selectedFont) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedFont) {
        if (pendingFont != uiState.selectedFont) {
            pendingFont = uiState.selectedFont
        }
    }

    val hasChanges = pendingFont != uiState.selectedFont

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Нижний бар рендерится над NavHost в AppNavigation.
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
                color = Secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FontSelector(
                selected = pendingFont,
                onSelected = { pendingFont = it }
            )
            Spacer(modifier = Modifier.height(24.dp))
            AppTextButton(
                onClick = { viewModel.applyFont(pendingFont) },
                textRes = R.string.apply,
                enabled = hasChanges,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSelector(
    selected: AppFont,
    onSelected: (AppFont) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.font_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth(),
            colors = appTextFieldColorsOnDark()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Tertiary
        ) {
            AppFont.entries.forEach { font ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = font.displayName,
                            fontFamily = font.fontFamily,
                            color = Primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    onClick = {
                        onSelected(font)
                        expanded = false
                    }
                )
            }
        }
    }
}
