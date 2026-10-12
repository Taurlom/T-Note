package ru.taurlom.tnote.presentation.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.taurlom.tnote.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Состояние защиты раздела «Документы» на время foreground-сессии.
 *
 * «Разблокировано» живёт только в памяти ViewModel: смерть процесса
 * раздел запирает сама, а уход приложения в фон запирает явно
 * ([onAppBackgrounded], зовётся из MainActivity.onStop).
 *
 * Скоуп — activity (как у остальных ViewModel разделов): и раздел, и
 * карточка документа видят одно состояние.
 */
@HiltViewModel
class SectionLockViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {

    /** true — текущая foreground-сессия прошла системное подтверждение. */
    private val sessionUnlocked = MutableStateFlow(false)

    /**
     * true — содержимое «Документов» должно быть закрыто заглушкой.
     * Начальное true — fail-closed: до первого ответа DataStore раздел
     * считается закрытым (сплэш всё равно перекрывает это окно).
     */
    val documentsLocked: StateFlow<Boolean> =
        combine(
            settingsRepository.documentsLockEnabled,
            sessionUnlocked,
        ) { enabled, unlocked -> enabled && !unlocked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onUnlocked() {
        sessionUnlocked.value = true
    }

    /** Запирание при уходе в фон (MainActivity.onStop). */
    fun onAppBackgrounded() {
        sessionUnlocked.value = false
    }
}
