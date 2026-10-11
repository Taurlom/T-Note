package ru.taurlom.tnote.presentation.error

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Единый канал ошибок действий UI: отказ use-case'а (запись в DataStore,
 * SecurityException на content://, …) не должен улетать в дефолтный
 * обработчик корутин и ронять приложение — он логируется и показывается
 * пользователю ненавязчивым snackbar'ом (см. MainActivity).
 *
 * Объект, а не Hilt-синглтон: каналу нечего подставлять в тестах, зато
 * им пользуются все ViewModel без правки конструкторов.
 */
object AppErrorBus {

    /** Событие «действие не выполнено»; текст единый — детали в логе. */
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun report(error: Throwable) {
        Log.e(TAG, "Действие не выполнено", error)
        _events.tryEmit(Unit)
    }

    private const val TAG = "AppErrorBus"
}

/**
 * viewModelScope.launch с обработкой ошибок по умолчанию: исключение
 * логируется и уходит в AppErrorBus (snackbar), а не в дефолтный
 * обработчик (краш). CancellationException пробрасывается — отмена
 * не ошибка. Действия с собственной реакцией на отказ (бэкап, шэring
 * списков) ловят сами — вложенный try/catch работает как раньше.
 */
fun ViewModel.launchCatching(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppErrorBus.report(e)
    }
}
