package ru.taurlom.tnote.presentation.widget

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обновления виджета «Сегодня»: изменения календаря и смена темы.
 *
 * Виджет живёт вне Compose-графа приложения, поэтому сам ни на Room,
 * ни на настройки не подписан. Здесь процессные наблюдатели: правка,
 * удаление или CalDAV-синхронизация (много записей подряд) схлопываются
 * в одно обновление через [DEBOUNCE_MILLIS], смена темы обновляет сразу.
 *
 * Системная тема («как в системе») меняется без записи в настройки,
 * поэтому дополнительно слушаем конфигурацию приложения.
 *
 * [start] вызывается из [ru.taurlom.tnote.TimeManagerApplication] — это
 * заодно первичная отрисовка после старта процесса и постановка
 * полуночного обновления ([TodayWidgetRefreshScheduler]).
 */
@Singleton
class TodayWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    // Скоуп процесса: наблюдатель живёт, пока живёт приложение.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val widget = TodayWidget()

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            updateWidget()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        (context.applicationContext as? Application)
            ?.registerComponentCallbacks(configurationCallbacks)

        // Полночное обновление: updatePeriodMillis у виджета неточный,
        // а после выключенного на ночь телефона дата иначе остаётся вчерашней.
        TodayWidgetRefreshScheduler.ensureScheduled(context)
        updateWidget()

        scope.launch {
            database.invalidationTracker
                .createFlow(SCHEDULED_EVENTS_TABLE, emitInitialState = false)
                .debounce(DEBOUNCE_MILLIS)
                .collect { updateWidget() }
        }
        scope.launch {
            settingsRepository.selectedTheme
                .distinctUntilChanged()
                // Первое значение уже прочитано первичной отрисовкой.
                .drop(1)
                .debounce(DEBOUNCE_MILLIS)
                // Ошибка чтения настроек не должна ронять процесс:
                // виджет останется в последней известной теме.
                .catch { }
                .collect { updateWidget() }
        }
    }

    private fun updateWidget() {
        scope.launch {
            // Ошибка обновления не должна убивать наблюдатель:
            // следующее изменение базы или темы попробует снова.
            runCatching { widget.updateAll(context) }
        }
    }

    private companion object {
        const val SCHEDULED_EVENTS_TABLE = "scheduled_events"
        const val DEBOUNCE_MILLIS = 500L
    }
}
