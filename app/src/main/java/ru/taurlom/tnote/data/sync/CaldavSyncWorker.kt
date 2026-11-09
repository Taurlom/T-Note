package ru.taurlom.tnote.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.taurlom.tnote.domain.repository.CaldavRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Доступ к графу Hilt из воркера: WorkManager создаёт воркеров своей
 * фабрикой рефлексией, @HiltWorker потребовал бы перенастройки
 * инициализации — см. подробный комментарий в DailyDigestDependencies.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CaldavSyncDependencies {
    fun caldavRepository(): CaldavRepository
}

/**
 * Фоновая синхронизация CalDAV-аккаунтов: каждые 15 минут при сети.
 * Все отказы репозиторий сам пишет в lastError аккаунтов — воркер
 * молча повторит по расписанию.
 */
class CaldavSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, CaldavSyncDependencies::class.java)
            .caldavRepository()
        repository.syncAll()
        return Result.success()
    }
}

/** Заводит и гасит периодический воркер; живёт при активных аккаунтах. */
@Singleton
class CaldavSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun ensurePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CaldavSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    /**
     * Экспедированный прогон после локальной правки (этап 2): правка,
     * набранная на телефоне, доезжает до календаря за секунды, а не к
     * следующей четверти. REPLACE собирает «залп» правок (десяток
     * пересохранений подряд) в один ближайший прогон; без сети прогоны
     * ждут констрейнта.
     */
    fun requestPush() {
        workManager.enqueueUniqueWork(
            ONE_TIME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CaldavSyncWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(UNIQUE_WORK)
    }

    private companion object {

        const val UNIQUE_WORK = "caldav-sync"
        const val ONE_TIME = "caldav-sync-onetime"
    }
}
