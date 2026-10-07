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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомление-сводка событий дня. Тап открывает раздел «Календарь»:
 * сводка всегда о сегодняшнем дне, а календарь стартует с текущего
 * месяца — попасть «в день» несложно и без диплинка на дату.
 *
 * MainActivity здесь — единственная активность приложения (оболочка,
 * а не экран): ссылка нужна только для PendingIntent тапа, и завязка
 * data → MainActivity в проекте с одной активностью — граница
 * приложения, а не нарушение слоёв.
 */
@Singleton
class DailyDigestNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun postDailyDigest(events: List<ScheduledEvent>) {
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

        val titles = events.map { it.title }
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
            .setContentText(titles.joinToString(", "))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")),
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
