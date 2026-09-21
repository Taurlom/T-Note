package com.example.timemanager.domain.model

import java.time.LocalDate
import java.time.Year

enum class ScheduledEventType { REGULAR, BIRTHDAY }

/**
 * Иконка обычного события. Отображается под числом дня в сетке календаря
 * и в списке событий дня. День рождения всегда рисуется подарком и
 * иконку не выбирает.
 */
enum class EventIcon { NOTE, MEETING, REPAIR, HOSPITAL, TRAVEL }

/**
 * Календарное событие: обычное событие или день рождения.
 *
 * [date] — якорная дата в формате "yyyy-MM-dd"; для дня рождения год
 * значения не имеет, используются только месяц и день.
 */
data class ScheduledEvent(
    val id: Long = 0,
    val date: String,
    val title: String,
    val type: ScheduledEventType = ScheduledEventType.REGULAR,
    val icon: EventIcon = EventIcon.NOTE,
    /** ARGB-цвет иконки; [DEFAULT_COLOR] — цвет из темы приложения. */
    val colorArgb: Long = DEFAULT_COLOR,
    val position: Int = 0
) {
    fun dateOrNull(): LocalDate? =
        date.let { runCatching { LocalDate.parse(it) }.getOrNull() }

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
