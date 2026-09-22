package com.example.timemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind
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
}
