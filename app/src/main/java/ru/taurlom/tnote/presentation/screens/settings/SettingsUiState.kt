package ru.taurlom.tnote.presentation.screens.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.notifications.DailyDigestSchedule
import ru.taurlom.tnote.domain.repository.BackupDescription
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.ThemeKind

/**
 * Результат операции с резервной копией; читается экраном один раз и
 * сбрасывается. Числа — из самопроверки архива: сколько фото реально в
 * копии и сколько отсутствовало ещё на момент её создания.
 */
sealed interface BackupResult {
    data class Exported(val photos: Int, val missing: Int) : BackupResult
    data class Imported(val photos: Int, val missing: Int) : BackupResult

    /**
     * Ошибка — типизированная причина вместо готовой строки: текст
     * собирает экран из ресурсов, иначе ViewModel хардкодил бы русские
     * подписи для английской и испанской локалей.
     */
    sealed interface Failed : BackupResult {
        /** Выбранный файл — не архив резервной копии T-Note. */
        data object NotABackup : Failed

        /**
         * Копия создана более новой версией приложения (формат архива или
         * схема БД новее): нужно обновиться, прежде чем восстанавливать.
         */
        data object NewerVersion : Failed

        /** Копия не читается: повреждена или её версия не поддерживается. */
        data object Unreadable : Failed

        /** Распакованный объём превысил лимит — защита от zip-бомбы. */
        data object TooLarge : Failed

        /** Любая другая ошибка чтения/записи; [detail] — текст исключения. */
        data class Error(val detail: String?) : Failed
    }
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
    /** Видимые разделы в порядке панели; пусто — порядок по умолчанию. */
    val visibleSections: List<String> = emptyList(),
    /**
     * DataStore ещё не ответил — порядок разделов неизвестен. Панель
     * держим пустой: иначе пейджер стартует с раздела по умолчанию,
     * «запоминает» его — и приезд реального порядка не переключает на
     * первый раздел пользователя.
     */
    val sectionsLoaded: Boolean = false,
    val isBackupBusy: Boolean = false,
    val backupResult: BackupResult? = null,
    /** Метаданные выбранного файла копии — для диалога подтверждения. */
    val pendingImport: BackupDescription? = null,
    /** Включена ли ежедневная сводка событий (уведомление). */
    val remindersEnabled: Boolean = false,
    /** Время сводки — минуты от полуночи (540 = 09:00). */
    val reminderTimeMinutes: Int = DailyDigestSchedule.DEFAULT_REMINDER_MINUTES,
    /**
     * DataStore ещё не ответил — значения напоминаний выше предварительны.
     * Тумблер до ответа отключён: мелькание «выключено → включено»
     * выглядело бы как самостоятельное переключение.
     */
    val remindersLoaded: Boolean = false
)
