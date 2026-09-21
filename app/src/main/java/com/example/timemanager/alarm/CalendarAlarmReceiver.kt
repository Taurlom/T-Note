package com.example.timemanager.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.domain.repository.ScheduledEventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Получатель сработавших будильников. Показывает уведомление, а для дня
 * рождения сразу планирует повтор на следующий год.
 */
@AndroidEntryPoint
class CalendarAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.example.timemanager.alarm.FIRE"
        const val EXTRA_EVENT_ID = "event_id"
    }

    @Inject
    lateinit var repository: ScheduledEventRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = repository.getById(eventId) ?: return@launch
                EventNotifications.showEvent(context, event)
                if (event.type == ScheduledEventType.BIRTHDAY && event.hasAlarm) {
                    // Повторяющееся событие: ставим будильник на следующий год.
                    alarmScheduler.scheduleNext(event)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
