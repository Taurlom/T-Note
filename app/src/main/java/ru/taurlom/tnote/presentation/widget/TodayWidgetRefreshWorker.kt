package ru.taurlom.tnote.presentation.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/**
 * Обновляет виджет «Сегодня» после полуночи: дата и события нового дня.
 * Отработав, ставит следующую задачу — цепочка живёт, пока виджет нужен.
 */
class TodayWidgetRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            TodayWidget().updateAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Следующая попытка — в полночь; до тех пор виджет дотянется
            // до свежих данных по updatePeriodMillis.
            Log.e(TAG, "Не удалось обновить виджет к новому дню", e)
        } finally {
            // APPEND_OR_REPLACE, а не REPLACE: новая задача встаёт в очередь
            // за текущей и не отменяет её на последнем шаге.
            TodayWidgetRefreshScheduler.ensureScheduled(
                applicationContext,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
            )
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "TodayWidgetRefresh"
    }
}
