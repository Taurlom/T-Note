package com.example.timemanager.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year

enum class ScheduledEventType { REGULAR, BIRTHDAY }

/**
 * Календарное событие: обычное событие или день рождения.
 *
 * [date] — якорная дата в формате "yyyy-MM-dd"; для дня рождения год
 * значения не имеет, используются только месяц и день.
 * [time] — "HH:mm" или null, если событие без конкретного времени.
 */
data class ScheduledEvent(
    val id: Long = 0,
    val date: String,
    val title: String,
    val time: String? = null,
    val type: ScheduledEventType = ScheduledEventType.REGULAR,
    val alarmEnabled: Boolean = false,
    val position: Int = 0
) {
    /** Будильник имеет смысл только у события с временем. */
    val hasAlarm: Boolean get() = alarmEnabled && !time.isNullOrBlank()

    fun timeOrNull(): LocalTime? =
        time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    fun dateOrNull(): LocalDate? =
        date.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Ближайший момент наступления события (для планирования будильника)
     * или null, если времени нет и момент недоступен.
     *
     * День рождения повторяется каждый год: 29.02 в невисокосные годы
     * переносится на 28.02.
     */
    fun nextTrigger(from: LocalDateTime): LocalDateTime? {
        val alarmTime = timeOrNull() ?: return null
        val anchor = dateOrNull() ?: return null
        return when (type) {
            ScheduledEventType.REGULAR -> {
                val trigger = LocalDateTime.of(anchor, alarmTime)
                if (trigger.isAfter(from)) trigger else null
            }
            ScheduledEventType.BIRTHDAY -> {
                (from.year..from.year + 1).firstNotNullOfOrNull { year ->
                    val (month, day) = effectiveBirthdayDate(anchor, year)
                    val trigger =
                        LocalDateTime.of(year, month, day, alarmTime.hour, alarmTime.minute)
                    if (trigger.isAfter(from)) trigger else null
                }
            }
        }
    }

    companion object {

        /** Фактическая month/day дня рождения [anchor] в году [year]. */
        fun effectiveBirthdayDate(anchor: LocalDate, year: Int): Pair<Int, Int> {
            val leap = Year.isLeap(year.toLong())
            return if (anchor.monthValue == 2 && anchor.dayOfMonth == 29 && !leap) {
                2 to 28
            } else {
                anchor.monthValue to anchor.dayOfMonth
            }
        }

        /** Отмечается ли день рождения [anchor] в дату [date]. */
        fun isBirthdayOn(anchor: LocalDate, date: LocalDate): Boolean {
            val (month, day) = effectiveBirthdayDate(anchor, date.year)
            return date.monthValue == month && date.dayOfMonth == day
        }
    }
}
