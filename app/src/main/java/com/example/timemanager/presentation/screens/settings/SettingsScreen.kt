package com.example.timemanager.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.presentation.components.AppDialog
import com.example.timemanager.presentation.components.AppDropdown
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.AppTheme
import com.example.timemanager.presentation.theme.ThemeKind
import java.time.LocalDate
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // Экспорт: пользователь сам выбирает, куда положить zip.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    // Импорт: сначала выбор файла, затем подтверждение замены данных.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it } }

    // Разовые уведомления по результату операции с копией.
    LaunchedEffect(uiState.backupResult) {
        when (val result = uiState.backupResult) {
            BackupResult.Exported ->
                Toast.makeText(context, R.string.backup_exported, Toast.LENGTH_SHORT).show()
            BackupResult.Imported -> showRestartDialog = true
            is BackupResult.Failed -> Toast.makeText(
                context,
                context.getString(R.string.backup_error, result.message),
                Toast.LENGTH_LONG
            ).show()
            null -> return@LaunchedEffect
        }
        viewModel.backupResultShown()
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        // Нижний бар лежит под пейджером в MainTabsScreen — его не учитываем.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
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
            Text(
                text = stringResource(R.string.backup_section),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.sectionTitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextButton(
                    onClick = {
                        exportLauncher.launch("tnote-backup-${LocalDate.now()}.zip")
                    },
                    textRes = R.string.backup_export,
                    enabled = !uiState.isBackupBusy,
                    modifier = Modifier.weight(1f)
                )
                AppTextButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream")
                        )
                    },
                    textRes = R.string.backup_import,
                    enabled = !uiState.isBackupBusy,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            AppTextButton(
                onClick = { showClearDialog = true },
                textRes = R.string.clear_calendar,
                modifier = Modifier.fillMaxWidth()
            )

            // Подвал экрана: версия для сверки с релизами и changelog.
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = remember(context) {
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    context.getString(
                        R.string.app_version,
                        info.versionName ?: "?",
                        PackageInfoCompat.getLongVersionCode(info)
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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

            pendingImportUri?.let { uri ->
                AppDialog(
                    title = stringResource(R.string.backup_import_confirm_title),
                    onDismissRequest = { pendingImportUri = null },
                    text = {
                        Text(
                            text = stringResource(R.string.backup_import_confirm_text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent
                        )
                    },
                    confirmButton = {
                        AppTextButton(
                            onClick = {
                                pendingImportUri = null
                                viewModel.importBackup(uri)
                            },
                            textRes = R.string.backup_restore
                        )
                    }
                )
            }

            if (showRestartDialog) {
                // Закрыть можно только кнопкой перезапуска: до него данные
                // наполовину старые, наполовину новые.
                AppDialog(
                    title = stringResource(R.string.backup_restart_title),
                    onDismissRequest = { },
                    text = {
                        Text(
                            text = stringResource(R.string.backup_restart_text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent
                        )
                    },
                    confirmButton = {
                        AppTextButton(
                            onClick = { restartApp(context) },
                            textRes = R.string.backup_restart_action
                        )
                    }
                )
            }
        }
    }
}

/**
 * Перезапуск процесса: импортированные базу и фото должен с чистого листа
 * открыть Room, а текущий процесс держит старые файлы открытыми.
 */
private fun restartApp(context: Context) {
    val intent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ?: return
    context.startActivity(intent)
    exitProcess(0)
}

