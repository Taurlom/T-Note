package ru.taurlom.tnote.presentation.update

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import ru.taurlom.tnote.domain.model.AppUpdateResult
import ru.taurlom.tnote.domain.repository.AppUpdateRepository
import ru.taurlom.tnote.presentation.error.launchCatching
import javax.inject.Inject

/**
 * Состояние обновлений приложения: диалог «доступна новая версия»
 * (MainActivity) и кнопка «Проверить обновления» (настройки).
 *
 * ViewModel живёт в скоупе активности: проверка при старте (init) не
 * перезапускается при повороте экрана, а закрытый «Позже» диалог не
 * возвращается до конца сессии. Само расписание «раз в сутки» живёт в
 * репозитории — частые запуски приложения переиспользуют последний
 * результат без повторного запроса.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(private val appUpdateRepository: AppUpdateRepository) : ViewModel() {

    /** Что показать в диалоге: новая версия и страница приложения в RuStore. */
    data class DialogState(val latestVersionName: String, val storePageUrl: String)

    /** Итог ручной проверки (кнопка в настройках), если обновления нет. */
    sealed interface ManualResult {
        data object UpToDate : ManualResult
        data object Unreachable : ManualResult
    }

    private val _dialogState = MutableStateFlow<DialogState?>(null)
    val dialogState: StateFlow<DialogState?> = _dialogState.asStateFlow()

    private val _manualResult = MutableStateFlow<ManualResult?>(null)
    val manualResult: StateFlow<ManualResult?> = _manualResult.asStateFlow()

    init {
        launchCatching {
            val result = appUpdateRepository.check(force = false)
            if (result is AppUpdateResult.Available) {
                showDialog(result.versionName)
            }
        }
    }

    /** Кнопка «Проверить обновления» в настройках: проверка без суточного окна. */
    fun checkManually() {
        launchCatching {
            when (val result = appUpdateRepository.check(force = true)) {
                is AppUpdateResult.Available -> showDialog(result.versionName)
                AppUpdateResult.UpToDate -> _manualResult.update { ManualResult.UpToDate }
                AppUpdateResult.Unreachable -> _manualResult.update { ManualResult.Unreachable }
            }
        }
    }

    /** Тост по ручной проверке показан — не повторять его на повороте экрана. */
    fun manualResultShown() {
        _manualResult.value = null
    }

    /** «Позже»: диалог закрывается до следующего запуска приложения. */
    fun dismissDialog() {
        _dialogState.value = null
    }

    /** «Пропустить эту версию»: диалог за неё больше не показывается. */
    fun skipVersion() {
        val state = _dialogState.value ?: return
        _dialogState.value = null
        launchCatching { appUpdateRepository.skip(state.latestVersionName) }
    }

    private suspend fun showDialog(versionName: String) {
        // Пропущенная версия не показывается, даже если фоновая проверка
        // нашла её раньше и сохранила в DataStore.
        if (appUpdateRepository.skippedVersion.first() != versionName) {
            _dialogState.value = DialogState(versionName, appUpdateRepository.storePageUrl())
        }
    }
}
