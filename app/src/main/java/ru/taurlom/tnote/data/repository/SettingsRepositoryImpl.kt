package ru.taurlom.tnote.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.notifications.DailyDigestSchedule
import ru.taurlom.tnote.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val fontKey = stringPreferencesKey("selected_font")
    private val themeKey = stringPreferencesKey("selected_theme")
    // У preferences 1.1.1 нет ключа для List<String>, а stringSet не хранит
    // порядок — список разделов живёт одной строкой через запятую (id —
    // стабильные ASCII-метки, экранирование не нужно).
    private val sectionsKey = stringPreferencesKey("visible_sections")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val reminderTimeMinutesKey = intPreferencesKey("reminder_time_minutes")

    override val selectedFont: Flow<AppFont> = dataStore.data
        .map { preferences ->
            AppFont.fromName(preferences[fontKey] ?: AppFont.PT_SANS.name)
        }

    override suspend fun setSelectedFont(font: AppFont) {
        dataStore.edit { preferences ->
            preferences[fontKey] = font.name
        }
    }

    override val selectedTheme: Flow<ThemeKind> = dataStore.data
        .map { preferences ->
            ThemeKind.fromName(preferences[themeKey] ?: ThemeKind.DARK.name)
        }

    override suspend fun setSelectedTheme(theme: ThemeKind) {
        dataStore.edit { preferences ->
            preferences[themeKey] = theme.name
        }
    }

    override val visibleSections: Flow<List<String>> = dataStore.data
        .map { preferences ->
            preferences[sectionsKey]?.split(SECTIONS_DELIMITER)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    override suspend fun setVisibleSections(sections: List<String>) {
        dataStore.edit { preferences ->
            preferences[sectionsKey] = sections.joinToString(SECTIONS_DELIMITER)
        }
    }

    // Напоминания выключены, пока пользователь не включил их явно: значения
    // по умолчанию подставляются здесь же, отдельного «первого запуска» нет.
    override val remindersEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[remindersEnabledKey] ?: false }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[remindersEnabledKey] = enabled
        }
    }

    override val reminderTimeMinutes: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[reminderTimeMinutesKey] ?: DailyDigestSchedule.DEFAULT_REMINDER_MINUTES
        }

    override suspend fun setReminderTimeMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[reminderTimeMinutesKey] = minutes
        }
    }

    private companion object {
        const val SECTIONS_DELIMITER = ","
    }
}
