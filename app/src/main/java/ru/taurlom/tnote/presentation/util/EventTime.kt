package ru.taurlom.tnote.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.util.formatEventClock

/**
 * Подпись времени события: «10:00 – 11:30» (начало и конец), «10:00» (только
 * начало) или «до 11:30» (только конец). null — у события нет времени, оно
 * остаётся дневной меткой.
 *
 * Формат часов — общий с уведомлением-сводкой ([formatEventClock]), текст
 * «до …» — локализованный.
 */
@Composable
fun eventTimeLabel(startMinutes: Int?, endMinutes: Int?): String? = eventTimeLabel(startMinutes to endMinutes)

/**
 * Подпись часов окна дня ([ru.taurlom.tnote.domain.model.ScheduledEvent.windowOn]):
 * пара «начало – конец» в минутах от полуночи.
 *
 * У события длиннее суток окно каждого дня своё: первый день — «23:30 –
 * 24:00», последний — «00:00 – 00:30», средние — «00:00 – 24:00».
 */
@Composable
fun eventTimeLabel(window: Pair<Int?, Int?>?): String? {
    val (from, until) = window ?: return null
    return when {
        from != null && until != null -> stringResource(
            R.string.event_time_range,
            formatEventClock(from),
            formatEventClock(until),
        )

        from != null -> formatEventClock(from)

        until != null -> stringResource(R.string.event_time_until, formatEventClock(until))

        else -> null
    }
}
