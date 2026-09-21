package com.example.timemanager.domain.usecase

import com.example.timemanager.alarm.AlarmScheduler
import com.example.timemanager.domain.repository.ScheduledEventRepository
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Перепланирует все включённые будильники на их ближайшие моменты.
 * Вызывается при старте приложения и после перезагрузки устройства.
 */
class SyncEventAlarmsUseCase @Inject constructor(
    private val repository: ScheduledEventRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke() {
        val now = LocalDateTime.now()
        repository.getAllWithAlarm().forEach { event ->
            // Тот же requestCode: старая pending-заявка заменяется новой.
            val scheduled = alarmScheduler.scheduleNext(event, now)
            if (!scheduled) alarmScheduler.cancel(event.id)
        }
    }
}
