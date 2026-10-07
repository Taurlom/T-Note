package ru.taurlom.tnote.data.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ru.taurlom.tnote.domain.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-реализация планировщика сводки. Цепочка задач
 * «OneTime → воркер ставит себе смену» вместо периодической:
 * у PeriodicWorkRequest интервал отсчитывается от фактического запуска
 * и постепенно уплывает по doze, а цепочка стартует каждый день заново
 * в назначенное время.
 *
 * Уникальное имя задачи — единственный «ключ» расписания; им же
 * политика из [ReminderScheduler.Mode] решает конфликт с уже висящей
 * задачей.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderScheduler {

    override suspend fun scheduleNextDigest(atEpochMillis: Long, mode: ReminderScheduler.Mode) {
        val policy = when (mode) {
            ReminderScheduler.Mode.RESCHEDULE -> ExistingWorkPolicy.REPLACE
            ReminderScheduler.Mode.IF_IDLE -> ExistingWorkPolicy.KEEP
            ReminderScheduler.Mode.AFTER_CURRENT -> ExistingWorkPolicy.APPEND_OR_REPLACE
        }
        // Задержка до момента срабатывания. Просрочка (системные часы
        // ушли назад, настройка применилась «в прошлое») стартует задачу
        // немедленно, а не улетает в отрицательное время.
        val delayMillis = (atEpochMillis - System.currentTimeMillis())
            .coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DAILY_DIGEST_WORK, policy, request)
    }

    override suspend fun cancelScheduledDigests() {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_DIGEST_WORK)
    }

    private companion object {
        const val DAILY_DIGEST_WORK = "daily_digest"
    }
}
