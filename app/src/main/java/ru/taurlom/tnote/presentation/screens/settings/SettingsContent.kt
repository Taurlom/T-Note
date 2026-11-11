package ru.taurlom.tnote.presentation.screens.settings

import android.Manifest
import android.accounts.AccountManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavProvider
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.repository.BackupDescription
import ru.taurlom.tnote.presentation.components.AppButton
import ru.taurlom.tnote.presentation.components.AppDialog
import ru.taurlom.tnote.presentation.components.AppDropdown
import ru.taurlom.tnote.presentation.components.AppTextButton
import ru.taurlom.tnote.presentation.components.AppTextField
import ru.taurlom.tnote.presentation.components.AppTimePicker
import ru.taurlom.tnote.presentation.components.ConfirmDeleteDialog
import ru.taurlom.tnote.presentation.lock.DeviceCredential
import ru.taurlom.tnote.presentation.lock.rememberDeviceCredentialVerifier
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.fontFamily
import ru.taurlom.tnote.presentation.theme.labelRes
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Содержимое экрана настроек: все секции собраны в одном скроллящемся
 * Column, чтобы SettingsScreen сосредоточился на скоупе состояний
 * и запускаторах активностей.
 *
 * @param contentPadding отступы от Scaffold (высота верхней панели):
 * без них первый элемент экрана уезжает под закреплённый TopAppBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    pendingImport: BackupDescription?,
    pendingImportUri: Uri?,
    viewModel: SettingsViewModel,
    notificationsBlocked: Boolean,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    exportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    importLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    showClearDialog: Boolean,
    onShowClearDialogChange: (Boolean) -> Unit,
    showPastDialog: Boolean,
    onShowPastDialogChange: (Boolean) -> Unit,
    showTimeDialog: Boolean,
    onShowTimeDialogChange: (Boolean) -> Unit,
    showRestartDialog: Boolean,
    onPendingImportUriChange: (Uri?) -> Unit,
    restartApp: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Внешний вид ──
        SectionTitle(stringResource(R.string.font_label))
        AppDropdown(
            label = stringResource(R.string.font_label),
            selectedLabel = uiState.selectedFont.displayName,
            options = AppFont.entries.toList(),
            optionText = { font ->
                Text(
                    text = font.displayName,
                    fontFamily = font.fontFamily,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.dialogContent,
                )
            },
            onSelect = { viewModel.applyFont(it) },
            onDarkBackground = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.theme_label))
        AppDropdown(
            label = stringResource(R.string.theme_label),
            selectedLabel = stringResource(uiState.selectedTheme.labelRes),
            options = ThemeKind.entries.toList(),
            optionText = { theme ->
                Text(
                    text = stringResource(theme.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.dialogContent,
                )
            },
            onSelect = { viewModel.applyTheme(it) },
            onDarkBackground = true,
            modifier = Modifier.fillMaxWidth(),
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
                    color = AppTheme.colors.dialogContent,
                )
            },
            onSelect = { viewModel.applyLanguage(it) },
            onDarkBackground = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Календарь ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.settings_calendar_section))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reminders_switch_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.reminders_switch_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = uiState.remindersEnabled,
                enabled = uiState.remindersLoaded,
                onCheckedChange = { enabled ->
                    viewModel.applyRemindersEnabled(enabled)
                    if (enabled && Build.VERSION.SDK_INT >= 33 && notificationsBlocked) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    }
                },
            )
        }
        if (uiState.remindersEnabled && notificationsBlocked) {
            Text(
                text = stringResource(R.string.reminders_blocked_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        AppButton(
            onClick = { onShowTimeDialogChange(true) },
            enabled = uiState.remindersEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    R.string.reminders_time_value,
                    formatReminderTime(uiState.reminderTimeMinutes),
                ),
            )
        }
        if (uiState.remindersEnabled) {
            Text(
                text = stringResource(R.string.reminders_battery_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextButton(
                onClick = { onShowPastDialogChange(true) },
                textRes = R.string.delete_past,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                onClick = { onShowClearDialogChange(true) },
                textRes = R.string.clear_calendar,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Синхронизация (CalDAV: Яндекс-календарь, этап 1 — чтение) ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.sync_section))
        CaldavSyncSection(viewModel)

        // ── Приватность ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.privacy_section))
        DocumentsLockSwitch(uiState, viewModel)

        // ── Резервная копия ──
        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.backup_section))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextButton(
                onClick = {
                    exportLauncher.launch("tnote-backup-${LocalDate.now()}.zip")
                },
                textRes = R.string.backup_export,
                enabled = !uiState.isBackupBusy,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream"),
                    )
                },
                textRes = R.string.backup_import,
                enabled = !uiState.isBackupBusy,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Настройка разделов ──
        Spacer(modifier = Modifier.height(24.dp))
        AppTextButton(
            onClick = { viewModel.openSectionsDialog() },
            textRes = R.string.sections_button,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Версия ──
        Spacer(modifier = Modifier.height(32.dp))
        val context = LocalContext.current
        Text(
            text = remember(context) {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                context.getString(
                    R.string.app_version,
                    info.versionName ?: "?",
                    PackageInfoCompat.getLongVersionCode(info),
                )
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Диалоги внутри Column ──

        if (showClearDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.clear_calendar),
                text = stringResource(R.string.clear_calendar_message),
                onDismiss = { onShowClearDialogChange(false) },
                onConfirm = {
                    viewModel.clearCalendar()
                    onShowClearDialogChange(false)
                },
            )
        }

        if (showPastDialog) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.delete_past),
                text = stringResource(R.string.delete_past_message),
                onDismiss = { onShowPastDialogChange(false) },
                onConfirm = {
                    onShowPastDialogChange(false)
                    viewModel.deletePastCalendar { cleanup ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.delete_past_done,
                                cleanup.events,
                                cleanup.notes,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        if (showTimeDialog) {
            val timeState = rememberTimePickerState(
                initialHour = uiState.reminderTimeMinutes / 60,
                initialMinute = uiState.reminderTimeMinutes % 60,
                is24Hour = true,
            )
            AppDialog(
                title = stringResource(R.string.reminders_time_dialog_title),
                onDismissRequest = { onShowTimeDialogChange(false) },
                confirmButton = {
                    AppTextButton(
                        onClick = {
                            viewModel.applyReminderTime(
                                timeState.hour * 60 + timeState.minute,
                            )
                            onShowTimeDialogChange(false)
                        },
                        textRes = R.string.done,
                    )
                },
                text = {
                    AppTimePicker(state = timeState)
                },
            )
        }

        pendingImportUri?.let { uri ->
            AppDialog(
                title = stringResource(R.string.backup_import_confirm_title),
                onDismissRequest = {
                    onPendingImportUriChange(null)
                    viewModel.dismissPendingImport()
                },
                text = {
                    Text(
                        text = stringResource(R.string.backup_import_confirm_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.dialogContent,
                    )
                    pendingImport?.let { meta ->
                        Text(
                            text = if (meta.missingPhotos > 0) {
                                stringResource(
                                    R.string.backup_import_meta_missing,
                                    formatDateTime(meta.createdAt),
                                    meta.photos,
                                    meta.missingPhotos,
                                )
                            } else {
                                stringResource(
                                    R.string.backup_import_meta,
                                    formatDateTime(meta.createdAt),
                                    meta.photos,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.dialogContentMuted,
                        )
                    }
                },
                confirmButton = {
                    AppTextButton(
                        onClick = {
                            onPendingImportUriChange(null)
                            viewModel.dismissPendingImport()
                            viewModel.importBackup(uri)
                        },
                        textRes = R.string.backup_restore,
                    )
                },
            )
        }

        if (showRestartDialog) {
            AppDialog(
                title = stringResource(R.string.backup_restart_title),
                onDismissRequest = { },
                text = {
                    Text(
                        text = stringResource(R.string.backup_restart_text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.colors.dialogContent,
                    )
                },
                confirmButton = {
                    AppTextButton(
                        onClick = restartApp,
                        textRes = R.string.backup_restart_action,
                    )
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.colors.sectionTitle,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

// ── CalDAV-подписка (Яндекс-календарь) ──

/**
 * Секция «Синхронизация»: карточка каждого подключённого календаря с
 * действиями «Обновить»/«Отключить» и кнопка формы подключения.
 */
@Composable
private fun CaldavSyncSection(viewModel: SettingsViewModel) {
    val accounts by viewModel.caldavAccounts.collectAsState()
    val busy by viewModel.caldavBusy.collectAsState()
    val localOnlyCount by viewModel.localOnlyCount.collectAsState()
    // Аккаунт, ждущий подтверждения отключения; null — диалог закрыт.
    var pendingDisconnect by remember { mutableStateOf<CaldavAccount?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.sync_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        accounts.forEach { account ->
            CaldavAccountCard(
                account = account,
                busy = busy,
                viewModel = viewModel,
                onDisconnectRequest = { pendingDisconnect = account },
            )
        }
        // Старые события (созданные до подключения подписки) ещё не видны
        // серверу: кнопка ставит их в очередь отправки. Прячется сама,
        // когда таких не остаётся.
        if (accounts.size == 1 && localOnlyCount > 0) {
            AppTextButton(
                onClick = { viewModel.publishLocalEvents(accounts[0].id) },
                textRes = R.string.sync_publish_old,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Один сторонний календарь — поддерживаемая пока конфигурация:
        // пока подписка подключена, форма добавления не предлагается.
        if (accounts.isEmpty()) {
            AppTextButton(
                onClick = viewModel::openCaldavDialog,
                textRes = R.string.sync_add_account,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    pendingDisconnect?.let { account ->
        ConfirmDeleteDialog(
            title = account.collectionName,
            text = stringResource(R.string.sync_disconnect_message),
            onDismiss = { pendingDisconnect = null },
            onConfirm = {
                viewModel.disconnectCaldavAccount(account.id)
                pendingDisconnect = null
            },
        )
    }

    CaldavAddAccountDialog(viewModel)
    CaldavSyncResultToast(viewModel)
    CaldavPublishResultToast(viewModel)
}

/** Итог «залить старые события» — тост с числом поставленных в очередь. */
@Composable
private fun CaldavPublishResultToast(viewModel: SettingsViewModel) {
    val published by viewModel.caldavPublishResult.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(published) {
        published?.let {
            Toast.makeText(
                context,
                context.getString(R.string.sync_publish_result, it),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.caldavPublishResultShown()
        }
    }
}

/** Карточка подписки: имена, последняя синхронизация (или ошибка), кнопки. */
@Composable
private fun CaldavAccountCard(account: CaldavAccount, busy: Boolean, viewModel: SettingsViewModel, onDisconnectRequest: () -> Unit) {
    val lastSyncText = remember(account.lastSyncAt) {
        if (account.lastSyncAt == 0L) {
            null
        } else {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(
                Instant.ofEpochMilli(account.lastSyncAt).atZone(ZoneId.systemDefault()),
            )
        }
    }
    val errorText = account.lastError?.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = account.collectionName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = account.username + " · " + account.serverUrl,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = errorText ?: (
                lastSyncText?.let { stringResource(R.string.sync_last_at, it) }
                    ?: stringResource(R.string.sync_last_never)
                ),
            style = MaterialTheme.typography.labelMedium,
            color = if (errorText != null) {
                Color(0xFFCC0000)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextButton(
                onClick = { viewModel.syncCaldavAccount(account.id) },
                textRes = R.string.sync_refresh,
                enabled = !busy,
            )
            AppTextButton(
                onClick = onDisconnectRequest,
                textRes = R.string.sync_disconnect,
                enabled = !busy,
            )
        }
    }
}

/** Итог ручной «Обновить» — тост с числами добавленных/обновлённых/удалённых/отправленных. */
@Composable
private fun CaldavSyncResultToast(viewModel: SettingsViewModel) {
    val result by viewModel.caldavResult.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(result) {
        result?.let {
            Toast.makeText(
                context,
                context.getString(R.string.sync_result, it.added, it.updated, it.removed, it.pushed),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.caldavResultShown()
        }
    }
}

/**
 * Форма «Подключение календаря»: сервер/логин/пароль приложения → поиск
 * → выбор календаря → подключение с первой синхронизацией.
 */
@Composable
private fun CaldavAddAccountDialog(viewModel: SettingsViewModel) {
    val state by viewModel.caldavAddState.collectAsState()
    if (!state.open) return

    // Глазок видимости пароля: rememberSaveable, чтобы состояние
    // пережило пересоздание Activity (смена темы/языка/поворот).
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // Выбор аккаунта Google: результат — адрес аккаунта
    // (AccountManager.KEY_ACCOUNT_NAME), после него сразу поиск календарей
    // (он же вызовет диалог согласия, если тот ещё не выдан).
    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val account = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!account.isNullOrBlank()) {
            viewModel.updateCaldavUsername(account)
            viewModel.discoverCaldavCalendars()
        }
    }
    // Диалог согласия Google закрыт — поиск календарей повторяется.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.discoverCaldavCalendars() }

    // Намерение согласия показывается, как только попало в состояние.
    LaunchedEffect(state.consentIntent) {
        state.consentIntent?.let { intent ->
            consentLauncher.launch(intent)
            viewModel.caldavConsentLaunched()
        }
    }

    AppDialog(
        title = stringResource(R.string.sync_add_title),
        onDismissRequest = viewModel::closeCaldavDialog,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Источник: Яндекс (CalDAV) или Google (Calendar API) —
                // один активный аккаунт, переключение сбрасывает форму.
                Text(
                    text = stringResource(R.string.sync_provider),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.dialogContent,
                )
                // Каждый вариант — своей строкой: в узком диалоге два
                // радио в ряд не помещаются, подпись сжималась в столбик.
                listOf(
                    CaldavProvider.YANDEX to R.string.sync_provider_yandex,
                    CaldavProvider.GOOGLE to R.string.sync_provider_google,
                ).forEach { (provider, labelRes) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.provider == provider,
                            onClick = { viewModel.selectCaldavProvider(provider) },
                            // Неактивная радиокнопка на фоне диалога
                            // терялась — красим и её цветом текста.
                            colors = radioColors(),
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent,
                        )
                    }
                }

                when (state.provider) {
                    CaldavProvider.YANDEX -> {
                        AppTextField(
                            value = state.server,
                            onValueChange = viewModel::updateCaldavServer,
                            label = stringResource(R.string.sync_server),
                            singleLine = true,
                        )
                        AppTextField(
                            value = state.username,
                            onValueChange = viewModel::updateCaldavUsername,
                            label = stringResource(R.string.sync_login),
                            singleLine = true,
                        )
                        AppTextField(
                            value = state.password,
                            onValueChange = viewModel::updateCaldavPassword,
                            label = stringResource(R.string.sync_password),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                // Показываем действие, которое произойдёт по нажатию:
                                // видимый пароль — иконка «скрыть», и наоборот.
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        painter = painterResource(
                                            if (passwordVisible) {
                                                R.drawable.ic_visibility_off
                                            } else {
                                                R.drawable.ic_visibility
                                            },
                                        ),
                                        contentDescription = stringResource(
                                            if (passwordVisible) {
                                                R.string.sync_password_hide
                                            } else {
                                                R.string.sync_password_show
                                            },
                                        ),
                                    )
                                }
                            },
                        )
                        Text(
                            text = stringResource(R.string.sync_password_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.dialogContent,
                        )
                    }

                    CaldavProvider.GOOGLE -> {
                        // Пароля нет: токен берётся OAuth'ом у выбранного аккаунта.
                        Text(
                            text = if (state.username.isNotBlank()) {
                                stringResource(R.string.sync_google_account, state.username)
                            } else {
                                stringResource(R.string.sync_google_account_none)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.colors.dialogContent,
                        )
                        Text(
                            text = stringResource(R.string.sync_google_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.colors.dialogContent,
                        )
                    }
                }

                if (state.calendars.isEmpty()) {
                    CaldavAddErrorText(state)
                } else {
                    Text(
                        text = stringResource(R.string.sync_pick),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.colors.dialogContent,
                    )
                    state.calendars.forEach { calendar ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = calendar.url == state.selectedUrl,
                                onClick = { viewModel.selectCaldavCalendar(calendar) },
                                colors = radioColors(),
                            )
                            Text(
                                text = calendar.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AppTheme.colors.dialogContent,
                            )
                        }
                    }
                    CaldavAddErrorText(state)
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                // У Google до поиска нужен выбранный аккаунт — кнопка выбора
                // живёт рядом с «Найти календари».
                if (state.provider == CaldavProvider.GOOGLE && state.calendars.isEmpty()) {
                    AppTextButton(
                        onClick = {
                            val picker = AccountManager.newChooseAccountIntent(
                                null,
                                null,
                                arrayOf("com.google"),
                                null,
                                null,
                                null,
                                null,
                            )
                            accountPickerLauncher.launch(picker)
                        },
                        textRes = R.string.sync_google_pick,
                        enabled = !state.discovering,
                    )
                }
                if (state.calendars.isEmpty()) {
                    AppTextButton(
                        onClick = viewModel::discoverCaldavCalendars,
                        textRes = R.string.sync_find,
                        enabled = when (state.provider) {
                            CaldavProvider.GOOGLE -> state.username.isNotBlank() && !state.discovering

                            CaldavProvider.YANDEX ->
                                state.username.isNotBlank() && state.password.isNotBlank() && !state.discovering
                        },
                    )
                } else {
                    AppTextButton(
                        onClick = viewModel::connectCaldavAccount,
                        textRes = R.string.sync_connect,
                        enabled = state.selectedUrl != null && !state.connecting,
                    )
                }
            }
        },
    )
}

/** Цвет радиокнопок в диалоге: активная и неактивная — цветом текста. */
@Composable
private fun radioColors() = RadioButtonDefaults.colors(
    selectedColor = AppTheme.colors.dialogContent,
    unselectedColor = AppTheme.colors.dialogContent,
)

/** Подпись отказа формы: типизированный вид из [CaldavErrorKind]. */
@Composable
private fun CaldavAddErrorText(state: CaldavAddState) {
    Text(
        text = when (state.error) {
            CaldavErrorKind.NETWORK -> stringResource(R.string.sync_error_network)
            CaldavErrorKind.AUTH -> stringResource(R.string.sync_error_auth)
            CaldavErrorKind.NO_CALENDARS -> stringResource(R.string.sync_error_no_calendars)
            CaldavErrorKind.CONSENT -> stringResource(R.string.sync_consent_needed)
            else -> stringResource(R.string.sync_error_other, state.errorMessage.orEmpty())
        },
        style = MaterialTheme.typography.bodySmall,
        // Системный errorColor в тёмной палитре малоконтрастен на фоне
        // диалога — читаемость важнее формальной темы.
        color = Color(0xFFCC0000),
    )
}

/**
 * Тумблер защиты «Документов». Любая смена — только через системное
 * подтверждение разблокировки телефона: иначе на разблокированном
 * телефоне чужой отключил бы защиту одним движением. Если блокировка
 * экрана не настроена, защищать нечем — подсказка тостом.
 */
@Composable
private fun DocumentsLockSwitch(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    // Куда переключить тумблер после подтверждения; null — запроса нет.
    var pendingLockChange by remember { mutableStateOf<Boolean?>(null) }
    val startVerification = rememberDeviceCredentialVerifier(
        title = stringResource(R.string.documents_lock_prompt_title),
        onResult = { confirmed ->
            val target = pendingLockChange
            pendingLockChange = null
            if (confirmed && target != null) {
                viewModel.applyDocumentsLockEnabled(target)
            }
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.documents_lock_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.documents_lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = uiState.documentsLockEnabled,
            onCheckedChange = { enabled ->
                if (DeviceCredential.isAvailable(context)) {
                    pendingLockChange = enabled
                    startVerification()
                } else {
                    Toast.makeText(
                        context,
                        R.string.documents_lock_unavailable,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }
}

private fun formatDateTime(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(millis)

/** «09:00» из минут от полуночи. Локаль фиксирована — разрядность и разделитель стабильны. */
private fun formatReminderTime(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
