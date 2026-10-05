package com.example.timemanager

import android.app.Application
import com.example.timemanager.domain.notifications.ReminderScheduler
import com.example.timemanager.domain.usecase.ScheduleNextDailyDigestUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TimeManagerApplication : Application() {

    // Скоуп процесса: живёт, пока живёт процесс, включая воркеров
    // WorkManager, поднимающих его без UI.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var scheduleNextDailyDigest: ScheduleNextDailyDigestUseCase

    override fun onCreate() {
        super.onCreate()
        // Самовосстановление цепочки сводки: задачи WorkManager переживают
        // перезагрузку и обновление приложения, но принудительная остановка
        // и агрессивная экономия батареи OEM цепочку срезают. Каждый старт
        // процесса тихо проверяет расписание; IF_IDLE не трогает живую
        // задачу — процесс мог быть поднят самим WorkManager'ом ради
        // бегущего воркера, и замена отменила бы его.
        applicationScope.launch {
            // Ошибка на старте не должна ронять приложение: расписание
            // подлечится при следующем запуске.
            runCatching { scheduleNextDailyDigest(ReminderScheduler.Mode.IF_IDLE) }
        }
    }
}
