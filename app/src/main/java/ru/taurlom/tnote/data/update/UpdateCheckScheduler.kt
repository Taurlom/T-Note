package ru.taurlom.tnote.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.taurlom.tnote.data.notifications.UpdateCheckWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ежедневная фоновая проверка обновлений: периодическая задача WorkManager
 * с условием «есть сеть». Ставится при каждом старте процесса: задачи
 * переживают перезагрузку и обновление приложения, но принудительная
 * остановка и агрессивная экономия батареи OEM их срезают — KEEP не
 * трогает живую задачу и восстанавливает срезанную.
 *
 * У PeriodicWorkRequest интервал плавает по doze — для уведомления
 * «вышла версия» точность «раз в сутки» не критична.
 */
@Singleton
class UpdateCheckScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UPDATE_CHECK_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val UPDATE_CHECK_WORK = "update_check"
    }
}
