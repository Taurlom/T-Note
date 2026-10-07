package ru.taurlom.tnote.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import ru.taurlom.tnote.domain.notifications.DailyDigestSchedule
import ru.taurlom.tnote.domain.notifications.ReminderScheduler
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Задача «показать сводку событий дня». Воркер не знает, на какое время
 * его ставили: настройки перечитываются при запуске — пользователь мог
 * перенести время, пока задача висела. Отработав, ставит себе смену на
 * завтра (AFTER_CURRENT) — так цепочка живёт, пока её не выключат.
 *
 * Конструктор (Context, WorkerParameters) — контракт фабрики WorkManager:
 * она строит воркеры рефлексией именно через него (см. keep-правило
 * в proguard-rules.pro).
 */
class DailyDigestWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            DailyDigestDependencies::class.java,
        )
        // Напоминания выключили, пока задача висела: не постим и цепочку
        // не продолжаем — включение в настройках построит её заново.
        if (!dependencies.settingsRepository().remindersEnabled.first()) {
            return Result.success()
        }
        try {
            val minutes = dependencies.settingsRepository().reminderTimeMinutes.first()
            // Задача стартовала раньше нового времени (время перенесли
            // позже уже после постановки): не постим, только перепланируем-
            // ся в конце.
            if (DailyDigestSchedule.isDigestTimeReached(ZonedDateTime.now(), minutes)) {
                val events = dependencies.eventsForDate()(LocalDate.now())
                // Пустой день — тишина: сводка не спамит, но цепочка ниже
                // продолжается на завтра.
                if (events.isNotEmpty()) {
                    dependencies.notifier().postDailyDigest(events)
                }
            }
        } catch (e: CancellationException) {
            // Отмена (выключение напоминаний гасит и бегущий воркер):
            // пробрасываем — перепланировка в этом случае не нужна.
            throw e
        } catch (e: Exception) {
            // Сводка — не критичная функция: ошибка чтения настроек или
            // базы не должна ронять цепочку, следующая задача ставится
            // ниже как ни в чём не бывало.
        }
        dependencies.scheduleNextDigest()(ReminderScheduler.Mode.AFTER_CURRENT)
        return Result.success()
    }
}
