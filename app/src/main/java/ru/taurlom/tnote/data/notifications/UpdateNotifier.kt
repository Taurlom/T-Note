package ru.taurlom.tnote.data.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.taurlom.tnote.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомление «доступна новая версия». Тап открывает страницу приложения
 * в RuStore (с установленным магазином — сразу в нём, иначе в браузере):
 * скачивание остаётся в магазине, приложение APK само не ставит.
 */
@Singleton
class UpdateNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun postUpdateAvailable(versionName: String, storePageUrl: String) {
        val manager = NotificationManagerCompat.from(context)
        // Пользователь отключил уведомления приложения или не дал
        // POST_NOTIFICATIONS на 13+: notify() бесполезен — тихо уходим.
        if (!manager.areNotificationsEnabled()) return

        // Канал создаётся перед первой публикацией (idempotent) — системы
        // без каналов (Android 7) просто игнорируют этот вызов.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_notification_channel))
                .build(),
        )

        val openStorePage = PendingIntent.getActivity(
            context,
            OPEN_STORE_REQUEST_CODE,
            Intent(Intent.ACTION_VIEW, Uri.parse(storePageUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Тот же фирменный глиф, что и у сводки дня (ic_small_icon.xml).
            .setSmallIcon(R.drawable.ic_small_icon)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, versionName))
            .setContentIntent(openStorePage)
            .setAutoCancel(true)
            .build()
        // Один и тот же id: новое уведомление заменяет предыдущее,
        // «шторка» не копит очередь повторных напоминаний.
        manager.notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "update_available"
        const val UPDATE_NOTIFICATION_ID = 1003
        const val OPEN_STORE_REQUEST_CODE = 1004
    }
}
