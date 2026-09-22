package com.example.timemanager.domain.model

import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

enum class ScheduledEventType { REGULAR, BIRTHDAY, REPEATING, WEEKEND }

/** Штатный период повторения; null — свой интервал «раз в N дней». */
enum class RepeatPeriod { MONTHLY, WEEKLY, DAILY }

/**
 * Иконка обычного события. Отображается под числом дня в сетке календаря
 * и в списке событий дня. День рождения всегда рисуется подарком и
 * иконку не выбирает.
 */
enum class EventIcon {
    NOTE, TRAVEL, FOREST, GIFTS, HOME, HEALTH, BEACH, FOOTPRINT,
    CELEBRATION, BUILD, FITNESS, GROUPS, DELIVERY, SELF_CARE, BAR, GARDEN
}

/**
 * Календарное событие: обычное, день рождения или повторяющееся.
 *
 * [date] — якорная дата в формате "yyyy-MM-dd"; для дня рождения год
 * значения не имеет, используются только месяц и день. Для повторяющегося
 * события [date] — первое вхождение, от которого считаются повторы.
 */
data class ScheduledEvent(
    val id: Long = 0,
    val date: String,
    val title: String,
    val type: ScheduledEventType = ScheduledEventType.REGULAR,
    val icon: EventIcon = EventIcon.NOTE,
    /** ARGB-цвет иконки; [DEFAULT_COLOR] — цвет из темы приложения. */
    val colorArgb: Long = DEFAULT_COLOR,
    /** Штатный период повторения; null при типе REPEATING — свой интервал. */
    val repeatPeriod: RepeatPeriod? = null,
    /** «Раз в N дней» — используется, когда [repeatPeriod] не выбран. */
    val repeatIntervalDays: Int? = null,
    /** Сколько дней всего длится повторение; 0 — без ограничения. */
    val repeatDays: Int = 0,
    /** Скрывать вхождения до сегодняшней даты («удалять прошедшие»). */
    val hidePastOccurrences: Boolean = false,
    val position: Int = 0
) {
    fun dateOrNull(): LocalDate? =
        date.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Происходит ли повторяющееся событие в дату [date]
     * (якорное первое вхождение тоже считается).
     */
    fun occursOn(date: LocalDate): Boolean {
        if (type != ScheduledEventType.REPEATING) return false
        val anchor = dateOrNull() ?: return false
        if (date.isBefore(anchor)) return false
        val daysSince = ChronoUnit.DAYS.between(anchor, date)
        if (repeatDays > 0 && daysSince >= repeatDays) return false
        return when {
            // Раз в месяц: то же число; 29-31 в коротких месяцах — на последний день.
            repeatPeriod == RepeatPeriod.MONTHLY ->
                date.dayOfMonth == minOf(anchor.dayOfMonth, date.lengthOfMonth())
            repeatPeriod == RepeatPeriod.WEEKLY -> daysSince % 7 == 0L
            repeatPeriod == RepeatPeriod.DAILY -> true
            // Свой интервал «раз в N дней».
            else -> {
                val interval = repeatIntervalDays?.takeIf { it > 0 } ?: return false
                daysSince % interval == 0L
            }
        }
    }

    companion object {

        /** Цвет не выбран: иконка красится тематическим цветом приложения. */
        const val DEFAULT_COLOR = 0L

        /** Фактическая month/day дня рождения [anchor] в году [year]. */
        fun effectiveBirthdayDate(anchor: LocalDate, year: Int): Pair<Int, Int> {
            val leap = Year.isLeap(year.toLong())
            return if (anchor.monthValue == 2 && anchor.dayOfMonth == 29 && !leap) {
                2 to 28
            } else {
                anchor.monthValue to anchor.dayOfMonth
            }
        }
    }
}
