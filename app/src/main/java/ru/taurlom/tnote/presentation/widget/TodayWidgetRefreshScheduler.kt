package ru.taurlom.tnote.presentation.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Полуночное обновление виджета «Сегодня».
 *
 * `updatePeriodMillis` в today_widget_info.xml — минимальные 30 минут, и
 * система не гарантирует точное время: после выключенного на ночь телефона
 * виджет может ещё полчаса показывать вчерашнюю дату. Одноразовая задача
 * WorkManager переживает перезагрузку, срабатывает сразу после полуночи
 * и ставит следующую.
 */
object TodayWidgetRefreshScheduler {

    /**
     * @param policy что делать с уже стоящей задачей. По умолчанию KEEP:
     * старт процесса мог быть вызван самим воркером, и REPLACE отменил бы
     * его до обновления виджета.
     */
    fun ensureScheduled(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        // Ошибка планирования не должна ронять процесс: у виджета остаётся
        // системное обновление по updatePeriodMillis.
        runCatching {
            val now = ZonedDateTime.now()
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val delayMillis = Duration.between(now, nextMidnight)
                .toMillis()
                .coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<TodayWidgetRefreshWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }

    private const val WORK_NAME = "today_widget_refresh"
}
