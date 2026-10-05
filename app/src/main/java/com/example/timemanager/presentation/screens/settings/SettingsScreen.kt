package com.example.timemanager.presentation.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.presentation.components.AppButton
import com.example.timemanager.presentation.components.AppDialog
import com.example.timemanager.presentation.components.AppDropdown
import com.example.timemanager.presentation.components.AppTextButton
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.ThemeKind
import com.example.timemanager.presentation.theme.AppTheme
import com.example.timemanager.presentation.theme.fontFamily
import com.example.timemanager.presentation.theme.labelRes
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Всё это — saveable: подтверждение очистки и особенно
    // pendingImportUri («файл выбран, ждём подтверждения») не должны
    // пропадать при пересоздании Activity и заставлять выбирать файл
    // заново. Uri — Parcelable, Bundle сохраняет его напрямую.
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeDialog by rememberSaveable { mutableStateOf(false) }

    // Уведомления заблокированы системой (отказ в POST_NOTIFICATIONS на
    // Android 13+ или системная настройка). Перечитываем на каждом
    // ON_RESUME: пользователь мог включить их в настройках Android и
    // вернуться — сноска должна исчезнуть сразу.
    var notificationsBlocked by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsBlocked =
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Разрешение спрашиваем в момент включения тумблера, а не при старте:
    // выключенное напоминание не должно требовать ничего.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsBlocked = !granted }

    // Экспорт: пользователь сам выбирает, куда положить zip.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    // Импорт: выбор файла → читаем шапку копии (дату, число фото) →
    // осознанное подтверждение → восстановление.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            viewModel.describeImport(it)
        }
    }

    // Разовые уведомления по результату операции с копией.
    LaunchedEffect(uiState.backupResult) {
        when (val result = uiState.backupResult) {
            is BackupResult.Exported -> Toast.makeText(
                context,
                if (result.missing > 0) {
                    context.getString(R.string.backup_exported_missing, result.missing)
                } else {
                    context.getString(R.string.backup_exported)
                },
                Toast.LENGTH_LONG
            ).show()
            is BackupResult.Imported -> {
                showRestartDialog = true
                Toast.makeText(
                    context,
                    if (result.missing > 0) {
                        context.getString(R.string.backup_imported_missing, result.photos, result.missing)
                    } else {
                        context.getString(R.string.backup_imported, result.photos)
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
            is BackupResult.Failed -> {
                val message = when (result) {
                    BackupResult.Failed.NotABackup ->
                        context.getString(R.string.backup_error_not_a_backup)
                    BackupResult.Failed.NewerVersion ->
                        context.getString(R.string.backup_error_newer_version)
                    BackupResult.Failed.Unreadable ->
                        context.getString(R.string.backup_error_unreadable)
                    BackupResult.Failed.TooLarge ->
                        context.getString(R.string.backup_error_too_large)
                    is BackupResult.Failed.Error ->
                        result.detail ?: context.getString(R.string.backup_error_unknown)
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.backup_error, message),
                    Toast.LENGTH_LONG
                ).show()
            }
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
                selectedLabel = stringResource(uiState.selectedTheme.labelRes),
                options = ThemeKind.entries.toList(),
                optionText = { theme ->
                    Text(
                        text = stringResource(theme.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.dialogContent
                    )
                },
                onSelect = { viewModel.applyTheme(it) },
                onDarkBackground = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            // Язык: «Как в системе» или явный выбор (пересоздаёт экран).
            AppDropdown(
                label = stringResource(R.string.language_label),
                selectedLabel = stringResource(uiState.selectedLanguage.labelRes),
                options = AppLanguage.entries.toList(),
                optionText = { language ->
                    Text(
                        text = stringResource(language.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.dialogContent
                    )
                },
                onSelect = { viewModel.applyLanguage(it) },
                onDarkBackground = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            // Календарь: напоминания и обслуживание раздела живут вместе —
            // это все настройки, у которых общий предмет.
            Text(
                text = stringResource(R.string.settings_calendar_section),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.sectionTitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Локальная сводка событий дня: без сети и серверов, задачу
            // ведёт WorkManager (переживает перезагрузку и doze). Switch —
            // стандартный Material 3, цвета подтянутся из colorScheme темы.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reminders_switch_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.reminders_switch_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = uiState.remindersEnabled,
                    // До ответа DataStore тумблер мёртв: случайный тап по
                    // ещё не загруженному состоянию перезаписал бы
                    // настоящую настройку.
                    enabled = uiState.remindersLoaded,
                    onCheckedChange = { enabled ->
                        viewModel.applyRemindersEnabled(enabled)
                        // На Android 12- разрешения нет — уведомления
                        // управляются только системной настройкой.
                        if (enabled && Build.VERSION.SDK_INT >= 33 && notificationsBlocked) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                )
            }
            // Тумблер включён, но система не даст показать сводку:
            // предупреждаем, а не молчим — иначе «включил и не приходит».
            if (uiState.remindersEnabled && notificationsBlocked) {
                Text(
                    text = stringResource(R.string.reminders_blocked_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppButton(
                onClick = { showTimeDialog = true },
                enabled = uiState.remindersEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        R.string.reminders_time_value,
                        formatReminderTime(uiState.reminderTimeMinutes)
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppTextButton(
                onClick = { showClearDialog = true },
                textRes = R.string.clear_calendar,
                modifier = Modifier.fillMaxWidth()
            )
            // Подсказка про фон: агрессивная экономия батареи (Xiaomi,
            // Honor и др.) срезает фоновые задачи — частая причина
            // «напоминания не приходят». Показываем только тем, кому
            // напоминания реально включены.
            if (uiState.remindersEnabled) {
                Text(
                    text = stringResource(R.string.reminders_battery_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                onClick = { viewModel.openSectionsDialog() },
                textRes = R.string.sections_button,
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

            // Время сводки: пикер Material 3 внутри обычного AppDialog.
            // Состояние пикера создаётся под диалогом: при повторном
            // открытии стартует с уже сохранённого времени, а не с
            // вчерашнего выбора.
            if (showTimeDialog) {
                val timeState = rememberTimePickerState(
                    initialHour = uiState.reminderTimeMinutes / 60,
                    initialMinute = uiState.reminderTimeMinutes % 60,
                    is24Hour = true
                )
                AppDialog(
                    title = stringResource(R.string.reminders_time_dialog_title),
                    onDismissRequest = { showTimeDialog = false },
                    confirmButton = {
                        AppTextButton(
                            onClick = {
                                viewModel.applyReminderTime(
                                    timeState.hour * 60 + timeState.minute
                                )
                                showTimeDialog = false
                            },
                            textRes = R.string.done
                        )
                    },
                    text = {
                        TimePicker(state = timeState)
                    }
                )
            }

            pendingImportUri?.let { uri ->
                AppDialog(
                    title = stringResource(R.string.backup_import_confirm_title),
                    onDismissRequest = {
                        pendingImportUri = null
                        viewModel.dismissPendingImport()
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.backup_import_confirm_text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent
                        )
                        // Метаданные копии: видно, ТОТ ли файл и не пустой ли он.
                        pendingImport?.let { meta ->
                            Text(
                                text = if (meta.missingPhotos > 0) {
                                    stringResource(
                                        R.string.backup_import_meta_missing,
                                        formatDateTime(meta.createdAt),
                                        meta.photos,
                                        meta.missingPhotos
                                    )
                                } else {
                                    stringResource(
                                        R.string.backup_import_meta,
                                        formatDateTime(meta.createdAt),
                                        meta.photos
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.dialogContentMuted
                            )
                        }
                    },
                    confirmButton = {
                        AppTextButton(
                            onClick = {
                                pendingImportUri = null
                                viewModel.dismissPendingImport()
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

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(millis)

/** «09:00» из минут от полуночи. Локаль фиксирована — разрядность и разделитель стабильны. */
private fun formatReminderTime(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

