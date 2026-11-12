package ru.taurlom.tnote.data.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import ru.taurlom.tnote.domain.model.AppUpdateResult
import ru.taurlom.tnote.domain.repository.AppUpdateRepository

/**
 * Ежедневная фоновая проверка «вышла ли новая версия». Функция тихая:
 * обновление найдено → уведомление, не найдено или сбой → тишина.
 *
 * Конструктор (Context, WorkerParameters) — контракт фабрики WorkManager:
 * она строит воркеров рефлексией именно через него (см. keep-правило
 * в proguard-rules.pro).
 */
class UpdateCheckWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            UpdateCheckDependencies::class.java,
        )
        try {
            val repository = dependencies.appUpdateRepository()
            val result = repository.check(force = false)
            if (result is AppUpdateResult.Available) {
                dependencies
                    .updateNotifier()
                    .postUpdateAvailable(result.versionName, repository.storePageUrl())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Проверка не должна ронять воркер — следующий запуск по
            // расписанию. Но молчать нельзя: без лога «уведомления об
            // обновлениях не приходят» не диагностируется.
            Log.e(TAG, "Проверка обновлений не отработала", e)
        }
        return Result.success()
    }

    private companion object {
        const val TAG = "UpdateCheckWorker"
    }
}

/**
 * Доступ к графу Hilt из воркера — тот же штатный механизм, что и у
 * сводки дня (см. DailyDigestDependencies): без новых зависимостей.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateCheckDependencies {
    fun appUpdateRepository(): AppUpdateRepository
    fun updateNotifier(): UpdateNotifier
}
