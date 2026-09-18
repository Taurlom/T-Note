package com.example.timemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.presentation.theme.AppFont
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val fontKey = stringPreferencesKey("selected_font")

    override val selectedFont: Flow<AppFont> = dataStore.data
        .map { preferences ->
            AppFont.fromName(preferences[fontKey] ?: AppFont.PT_SANS.name)
        }

    override suspend fun setSelectedFont(font: AppFont) {
        dataStore.edit { preferences ->
            preferences[fontKey] = font.name
        }
    }
}
