package com.example.timemanager.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timemanager.domain.usecase.SyncEventAlarmsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Восстанавливает будильники после перезагрузки, обновления приложения или
 * смены времени/часового пояса: PendingIntent живут только до этих событий.
 */
@AndroidEntryPoint
class CalendarBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var syncEventAlarms: SyncEventAlarmsUseCase

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmScheduler.ACTION_RESYNC -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncEventAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
