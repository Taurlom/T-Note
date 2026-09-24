package com.example.timemanager.presentation.screens.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.timemanager.R
import com.example.timemanager.domain.repository.BackupDescription
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind

/**
 * Результат операции с резервной копией; читается экраном один раз и
 * сбрасывается. Числа — из самопроверки архива: сколько фото реально в
 * копии и сколько отсутствовало ещё на момент её создания.
 */
sealed interface BackupResult {
    data class Exported(val photos: Int, val missing: Int) : BackupResult
    data class Imported(val photos: Int, val missing: Int) : BackupResult
    data class Failed(val message: String) : BackupResult
}

/**
 * Язык интерфейса. `tag` — язык для LocaleListCompat; null означает
 * «как в системе» (пустой список локалей). Название варианта всегда
 * показывается на своём языке (Русский / English / Español), кроме
 * системного — он подписан по-текущему.
 */
enum class AppLanguage(val tag: String?, @StringRes val labelRes: Int) {
    SYSTEM(null, R.string.language_system),
    RU("ru", R.string.language_ru),
    EN("en", R.string.language_en),
    ES("es", R.string.language_es);

    companion object {
        /** Язык, применённый сейчас (per-app locales AppCompat). */
        fun current(): AppLanguage {
            val locales: LocaleListCompat = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val tag = locales[0]?.language
            return entries.firstOrNull { it.tag != null && it.tag == tag } ?: SYSTEM
        }
    }
}

data class SettingsUiState(
    val selectedFont: AppFont = AppFont.PT_SANS,
    val selectedTheme: ThemeKind = ThemeKind.DARK,
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    val isBackupBusy: Boolean = false,
    val backupResult: BackupResult? = null,
    /** Метаданные выбранного файла копии — для диалога подтверждения. */
    val pendingImport: BackupDescription? = null
)
