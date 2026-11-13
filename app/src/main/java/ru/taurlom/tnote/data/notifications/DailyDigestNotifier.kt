package ru.taurlom.tnote.data.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.taurlom.tnote.MainActivity
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.util.formatEventClock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомление-сводка событий дня. Тап открывает раздел «Календарь»:
 * сводка всегда о сегодняшнем дне, а календарь стартует с текущего
 * месяца — попасть «в день» несложно и без диплинка на дату.
 *
 * В сводке у события с часами они показываются перед названием
 * («10:00 – 11:30 Встреча»), у дневной метки — только название.
 *
 * MainActivity здесь — единственная активность приложения (оболочка,
 * а не экран): ссылка нужна только для PendingIntent тапа, и завязка
 * data → MainActivity в проекте с одной активностью — граница
 * приложения, а не нарушение слоёв.
 */
@Singleton
class DailyDigestNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * @param day день сводки: у события длиннее суток показывается его часть
     * за этот день, а не весь интервал.
     */
    fun postDailyDigest(events: List<ScheduledEvent>, day: LocalDate? = null) {
        if (events.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        // Пользователь отключил уведомления приложения или не дал
        // POST_NOTIFICATIONS на 13+: notify() бесполезен — тихо уходим.
        if (!manager.areNotificationsEnabled()) return

        // Канал создаётся перед первой публикацией (idempotent) — системы
        // без каналов (Android 7) просто игнорируют этот вызов.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.digest_notification_channel))
                .build(),
        )

        val lines = events.map { eventLine(it, day) }
        val count = events.size
        val openCalendar = PendingIntent.getActivity(
            context,
            OPEN_CALENDAR_REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CALENDAR, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Фирменный глиф (ic_small_icon.xml, конвертация из
            // design/ic_small_icon.svg): статус-бар красит малую иконку
            // по альфа-маске, поэтому заливка в XML белая.
            .setSmallIcon(R.drawable.ic_small_icon)
            // Цветной логотип — «большая» иконка слева в уведомлении.
            .setLargeIcon(decodeLargeIcon())
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.digest_notification_title, count, count),
            )
            // Свёрнутое — одна строка с перечислением; развёрнутое — список.
            .setContentText(lines.joinToString(", "))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")),
            )
            .setContentIntent(openCalendar)
            .setAutoCancel(true)
            .build()
        // Один и тот же id: вчерашняя сводка заменяется сегодняшней,
        // «шторка» не копит их очередь.
        manager.notify(DIGEST_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "daily_digest"
        const val DIGEST_NOTIFICATION_ID = 1001
        const val OPEN_CALENDAR_REQUEST_CODE = 1002
        const val LARGE_ICON_DP = 64
    }

    /**
     * Строка события в сводке: с часами — «10:00 – 11:30 Встреча», без
     * времени — просто название (событие осталось дневной меткой).
     *
     * Часы — в том же виде, что в списке событий дня: формат 24-часовой и
     * не зависит от локали, а текст «до …» (событие только с концом) —
     * локализованный.
     *
     * @param day день сводки: у события длиннее суток показывается его часть
     * за этот день ([ScheduledEvent.windowOn]), а не весь интервал.
     */
    internal fun eventLine(event: ScheduledEvent, day: LocalDate? = null): String {
        val window = day?.let { event.windowOn(it) } ?: (event.startMinutes to event.endMinutes)
        val start = window.first
        val end = window.second
        val clock = when {
            start != null && end != null -> context.getString(
                R.string.event_time_range,
                formatEventClock(start),
                formatEventClock(end),
            )

            start != null -> formatEventClock(start)

            end != null -> context.getString(R.string.event_time_until, formatEventClock(end))

            else -> null
        }
        return if (clock == null) event.title else "$clock ${event.title}"
    }

    /**
     * Логотип для большой иконки, декодированный под реальный размер
     * показа (~64dp): исходник 512px, и гнать его целиком в память ради
     * уголка уведомления незачем. inScaled=false обязателен: файл лежит
     * в res/drawable (считается mdpi), и без этого decodeResource
     * растянул бы картинку ещё и по плотности — на xxhdpi 512px
     * превратились бы в 1536.
     */
    private fun decodeLargeIcon(): Bitmap? {
        val targetPx = (LARGE_ICON_DP * context.resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(64)
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            inScaled = false
        }
        BitmapFactory.decodeResource(context.resources, R.drawable.shop_icon, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetPx &&
            bounds.outHeight / (sampleSize * 2) >= targetPx
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inScaled = false
        }
        return BitmapFactory.decodeResource(context.resources, R.drawable.shop_icon, options)
    }
}
