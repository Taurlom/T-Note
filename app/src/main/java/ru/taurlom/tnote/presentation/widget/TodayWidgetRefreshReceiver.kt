package ru.taurlom.tnote.presentation.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Системные события, после которых «Сегодня» должно перерисоваться сразу:
 * загрузка телефона, перевод часов или часового пояса, смена языка.
 *
 * `ACTION_DATE_CHANGED` начиная с Android 8 нельзя принимать манифестом,
 * поэтому полночь закрывает [TodayWidgetRefreshWorker].
 */
class TodayWidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Обновление виджета — асинхронное; goAsync не даёт системе убить
        // процесс, пока RemoteViews не перестроены.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                TodayWidget().updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось обновить виджет после ${intent.action}", e)
            } finally {
                // Часы/пояс могли переехать — пересчитываем ближайшую полночь
                // заново, а не оставляем старое время.
                TodayWidgetRefreshScheduler.ensureScheduled(
                    context,
                    ExistingWorkPolicy.REPLACE,
                )
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TodayWidgetRefresh"
    }
}
