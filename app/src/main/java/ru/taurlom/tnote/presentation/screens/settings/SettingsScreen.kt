package ru.taurlom.tnote.presentation.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.components.AppTopBar
import kotlin.system.exitProcess

/**
 * Экран настроек: внешний вид, календарь-напоминания, резервные копии.
 * Состояния диалогов и запускаторы активностей живут здесь;
 * скроллящееся содержимое вынесено в [SettingsContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showRestartDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeDialog by rememberSaveable { mutableStateOf(false) }

    var notificationsBlocked by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsBlocked =
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsBlocked = !granted }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            viewModel.describeImport(it)
        }
    }

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
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        SettingsContent(
            uiState = uiState,
            pendingImport = pendingImport,
            pendingImportUri = pendingImportUri,
            viewModel = viewModel,
            notificationsBlocked = notificationsBlocked,
            notificationPermissionLauncher = notificationPermissionLauncher,
            exportLauncher = exportLauncher,
            importLauncher = importLauncher,
            showClearDialog = showClearDialog,
            onShowClearDialogChange = { showClearDialog = it },
            showTimeDialog = showTimeDialog,
            onShowTimeDialogChange = { showTimeDialog = it },
            showRestartDialog = showRestartDialog,
            onPendingImportUriChange = { pendingImportUri = it },
            restartApp = { restartApp(context) }
        )
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