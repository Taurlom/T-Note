package ru.taurlom.tnote.domain.model

import java.time.LocalDate
import java.util.Locale

/**
 * Разворот событий во вхождения — ЕДИНСТВЕННОЕ место, где живут правила:
 * обычные события берутся по точной дате; день рождения разворачивается
 * в дату-вхождение нужного года (29 февраля в невисокосный год
 * сдвигается на 28-е); повторяющееся — во все вхождения по occursOn с
 * учётом «удалять прошедшие» (скрыты дни ДО сегодняшнего, само
 * сегодняшнее вхождение видно). Якорные вхождения не задваиваются:
 * они уже приходят в месячной выборке.
 *
 * Правила общие для сетки календаря (forMonth) и сводки дня
 * (forDate через GetEventsForDateUseCase): изменение закона —
 * одна правка, а не две синхронные.
 */
object EventExpander {

    /** Вхождения на конкретную дату, в порядке списка дня календаря. */
    fun forDate(
        monthEvents: List<ScheduledEvent>,
        birthdays: List<ScheduledEvent>,
        repeating: List<ScheduledEvent>,
        date: LocalDate,
        today: LocalDate,
    ): List<ScheduledEvent> {
        val dateKey = date.toString()
        // Якоря повторов приходят в месячной выборке, а их вхождения
        // генерируются ниже целиком — иначе якорь задублировался бы.
        val result = monthEvents.filterTo(mutableListOf()) {
            it.date == dateKey && it.type != ScheduledEventType.REPEATING
        }
        birthdays.forEach { birthday ->
            birthdayOccurrence(birthday, date.year)?.let { (month, day) ->
                if (month == date.monthValue && day == date.dayOfMonth) {
                    result += birthday.copy(date = dateKey)
                }
            }
        }
        repeating.forEach { event ->
            if (event.occursOn(date) && event.isVisibleOn(date, today)) {
                result += event.copy(date = dateKey)
            }
        }
        return result.sortedWith(EVENT_ORDER)
    }

    /** Вхождения по дням месяца — для сетки календаря. */
    fun forMonth(
        monthEvents: List<ScheduledEvent>,
        birthdays: List<ScheduledEvent>,
        repeating: List<ScheduledEvent>,
        year: Int,
        month: Int,
        today: LocalDate,
    ): Map<String, List<ScheduledEvent>> {
        val byDate = monthEvents
            .filterNot { it.type == ScheduledEventType.REPEATING }
            .groupByTo(LinkedHashMap()) { it.date }
        birthdays.forEach { birthday ->
            birthdayOccurrence(birthday, year)?.let { (m, d) ->
                // Формат ISO-даты не должен зависеть от локали устройства.
                val key = String.format(LOCALE, "%04d-%02d-%02d", year, m, d)
                byDate.getOrPut(key) { mutableListOf() } += birthday.copy(date = key)
            }
        }
        val first = LocalDate.of(year, month, 1)
        val lastExclusive = first.plusMonths(1)
        repeating.forEach { event ->
            val anchor = event.dateOrNull() ?: return@forEach
            var cursor = maxOf(anchor, first)
            while (cursor.isBefore(lastExclusive)) {
                if (event.occursOn(cursor) && event.isVisibleOn(cursor, today)) {
                    val key = cursor.toString()
                    byDate.getOrPut(key) { mutableListOf() } += event.copy(date = key)
                }
                cursor = cursor.plusDays(1)
            }
        }
        return byDate.mapValues { (_, events) -> events.sortedWith(EVENT_ORDER) }
    }

    /**
     * Дата-вхождение дня рождения в году [year] или null, когда вхождение
     * уже пришло в месячной выборке (якорный год) либо якорь неразборчив.
     */
    private fun birthdayOccurrence(birthday: ScheduledEvent, year: Int): Pair<Int, Int>? {
        val anchor = birthday.dateOrNull() ?: return null
        // Вхождение якорного года уже пришло из месячного запроса.
        if (anchor.year == year) return null
        return ScheduledEvent.effectiveBirthdayDate(anchor, year)
    }

    /** «Удалять прошедшие»: скрыты дни ДО сегодняшнего, сегодня видно. */
    private fun ScheduledEvent.isVisibleOn(date: LocalDate, today: LocalDate): Boolean = !hidePastOccurrences || !date.isBefore(today)

    /** Порядок списка событий дня — в календаре и сводке одинаковый. */
    private val EVENT_ORDER: Comparator<ScheduledEvent> = compareBy({ it.position }, { it.id })

    private val LOCALE: Locale = Locale.US
}
