package ru.taurlom.tnote.domain.notifications

import java.time.ZonedDateTime

/**
 * Чистая арифметика расписания ежедневной сводки событий.
 *
 * Время сводки хранится минутами от полуночи (540 = 09:00), а не строкой
 * «09:00»: число валидируется диапазоном, сортируется и не требует парсинга.
 *
 * Расчёт ведётся «настенным» временем зоны устройства: пользователь просил
 * «в 9:00» — и в день перевода часов сводка придёт в 9:00 по новому
 * времени. Поэтому момент считается через [java.time.LocalDate.atTime]
 * (строка локального времени → зона), а не через плюс-минуты к мгновению:
 * `ZonedDateTime.plusMinutes` складывает реальные 540 минут, и в день
 * перевода вперёд это дало бы 10:00 вместо заказанных 9:00.
 *
 * Все функции чистые, «сейчас» приходит параметром — границы и перевод
 * часов проверяются тестами без Android.
 */
object DailyDigestSchedule {

    /** 09:00 — время по умолчанию, пока пользователь не выбрал своё. */
    const val DEFAULT_REMINDER_MINUTES: Int = 9 * 60

    /**
     * Следующий момент срабатывания: сегодня в [minutesFromMidnight], если
     * он ещё впереди, иначе завтра. Ровно в момент срабатывания
     * возвращается завтра — «сейчас» уже обрабатывает сегодняшняя задача,
     * дубль ей не нужен.
     */
    fun nextDigestTime(now: ZonedDateTime, minutesFromMidnight: Int): ZonedDateTime {
        val today = wallClockTime(now.toLocalDate(), minutesFromMidnight, now.zone)
        return if (today.isAfter(now)) today
        else wallClockTime(now.toLocalDate().plusDays(1), minutesFromMidnight, now.zone)
    }

    /**
     * Наступило ли время сводки сегодня. Задача стартует не раньше своей
     * задержки, но время в настройках могли перенести позже уже после
     * постановки: раньше нового времени постить нельзя.
     */
    fun isDigestTimeReached(now: ZonedDateTime, minutesFromMidnight: Int): Boolean =
        !wallClockTime(now.toLocalDate(), minutesFromMidnight, now.zone).isAfter(now)

    /** Дата + минуты от полуночи → момент в зоне (настенное время). */
    private fun wallClockTime(
        date: java.time.LocalDate,
        minutesFromMidnight: Int,
        zone: java.time.ZoneId
    ): ZonedDateTime =
        date.atTime(minutesFromMidnight / 60, minutesFromMidnight % 60).atZone(zone)
}
