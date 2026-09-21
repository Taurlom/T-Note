package com.example.timemanager.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.timemanager.domain.model.ScheduledEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Постановка точных будильников через [AlarmManager].
 *
 * В манифесте заявлены USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM: на Android 12+
 * разрешение выдаётся как ключевая функция приложения. На случай ручной отмены
 * предусмотрен запасной неточный сценарий и переход в системные настройки.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Служебное действие для принудительной перепланировки будильников. */
        const val ACTION_RESYNC = "com.example.timemanager.alarm.RESYNC"
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

    /** Ставит будильник на [triggerAt]; requestCode совпадает с id события. */
    fun schedule(event: ScheduledEvent, triggerAt: LocalDateTime) {
        val millis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = firePendingIntent(event.id)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } catch (_: SecurityException) {
            // Нет права на точные будильники — хотя бы сработает позже.
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                millis,
                TimeUnit.MINUTES.toMillis(15),
                pending
            )
        }
    }

    /**
     * Планирует ближайший момент события.
     *
     * @return false, если у события нет времени или оно уже безвозвратно прошло.
     */
    fun scheduleNext(event: ScheduledEvent, from: LocalDateTime = LocalDateTime.now()): Boolean {
        val trigger = event.nextTrigger(from) ?: return false
        schedule(event, trigger)
        return true
    }

    fun cancel(eventId: Long) {
        val pending = firePendingIntent(eventId)
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /** Открывает системный экран «Будильники и напоминания» (Android 12+). */
    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun firePendingIntent(eventId: Long): PendingIntent {
        // Сигнал содержит только id: название и тип получатель читает из базы,
        // поэтому редактирование события не требует пересоздания PendingIntent.
        val intent = Intent(context, CalendarAlarmReceiver::class.java).apply {
            action = CalendarAlarmReceiver.ACTION_FIRE
            putExtra(CalendarAlarmReceiver.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
