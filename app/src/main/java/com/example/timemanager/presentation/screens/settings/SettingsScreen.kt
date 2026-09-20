package com.example.timemanager.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.timemanager.presentation.components.BottomNavBar
import com.example.timemanager.presentation.components.BottomNavItem
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.DialogButtonBackground
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.OnTertiary
import com.example.timemanager.presentation.theme.Primary
import com.example.timemanager.presentation.theme.Tertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToCategories: () -> Unit
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarBackground,
                    titleContentColor = OnTertiary,
                    navigationIconContentColor = OnTertiary,
                    actionIconContentColor = OnTertiary
                )
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedItem = BottomNavItem.Settings,
                onItemSelected = { item ->
                    when (item) {
                        BottomNavItem.Categories -> onNavigateToCategories()
                        BottomNavItem.Calendar -> onNavigateToCalendar()
                        BottomNavItem.Documents -> onNavigateToDocuments()
                        else -> { /* Settings already active */ }
                    }
                }
            )
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
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            FontSelector(
                selected = pendingFont,
                onSelected = { pendingFont = it }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.applyFont(pendingFont) },
                enabled = hasChanges,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary,
                    disabledContainerColor = DialogButtonBackground.copy(alpha = 0.5f),
                    disabledContentColor = OnPrimary.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showClearDialog = true },
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DialogButtonBackground,
                    contentColor = OnPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.clear_calendar))
            }

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
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Primary,
                unfocusedTextColor = Primary,
                disabledTextColor = Primary,
                cursorColor = Primary,
                focusedBorderColor = Primary,
                unfocusedBorderColor = Primary.copy(alpha = 0.6f),
                focusedLabelColor = Primary,
                unfocusedLabelColor = Primary.copy(alpha = 0.6f),
                focusedTrailingIconColor = Primary,
                unfocusedTrailingIconColor = Primary
            )
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
