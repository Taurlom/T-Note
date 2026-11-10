package ru.taurlom.tnote.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Контракт маппера «событие ↔ iCalendar»: свои события переживают ICS-текст
 * без потерь, чужие VEVENT разбираются в то, что модель умеет, а не в отсебятину.
 */
class EventIcsMapperTest {

    private fun regular(
        date: String = "2026-02-17",
        title: String = "Ремонт",
        icon: EventIcon = EventIcon.BUILD,
        color: Long = ScheduledEvent.DEFAULT_COLOR,
        hidePast: Boolean = false,
    ) = ScheduledEvent(
        uid = "11111111-2222-3333-4444-555555555555",
        date = date,
        title = title,
        type = ScheduledEventType.REGULAR,
        icon = icon,
        colorArgb = color,
        hidePastOccurrences = hidePast,
        position = 4,
    )

    private fun repeating(
        anchor: String = "2026-01-01",
        unit: RepeatUnit? = RepeatUnit.DAYS,
        interval: Int = 3,
        weekdays: Set<DayOfWeek> = emptySet(),
        until: String? = null,
    ) = ScheduledEvent(
        uid = "aaaaaaa1-0000-0000-0000-000000000001",
        date = anchor,
        title = "Зарядка",
        type = ScheduledEventType.REPEATING,
        repeatUnit = unit,
        repeatInterval = interval,
        repeatWeekdays = weekdays,
        repeatUntil = until,
        icon = EventIcon.FITNESS,
    )

    private fun birthday(anchor: String = "1988-08-09", title: String = "ДР Маши") = ScheduledEvent(
        uid = "bbbbbbb1-0000-0000-0000-000000000002",
        date = anchor,
        title = title,
        type = ScheduledEventType.BIRTHDAY,
    )

    /** Свой VEVENT → ICS-текст → парс → модель: сравнение по полям. */
    private fun roundTrip(event: ScheduledEvent): ScheduledEvent {
        val ics = eventCalendar(listOf(event.toIcsEvent())).icsText()
        val calendar = parseIcs(ics) ?: error("ICS-текст не разобрался: $ics")
        val events = calendar.vevents()
        assertEquals(1, events.size)
        val parsed = events[0].toScheduledEvent() ?: error("VEVENT не распознан: $ics")
        // Без заданных CREATED/LAST-MODIFIED экспорт пишет только DTSTAMP
        // «сейчас», и разбор заполняет метки временем теста. Для источников
        // без меток такие значения выравниваем на null; события с метками
        // проверяются строго в `own timestamps round-trip`.
        return if (event.createdAt == null && event.modifiedAt == null) {
            parsed.copy(createdAt = null, modifiedAt = null)
        } else {
            parsed
        }
    }

    // Круговорот своих событий

    @Test
    fun `regular event with all fields round-trips through ics text`() {
        val source = regular(color = 0xFFE53935, hidePast = true)
        val parsed = roundTrip(source)
        // position и id локальные — в ICS не экспортируются.
        assertEquals(source.copy(position = 0, id = 0), parsed)
    }

    @Test
    fun `default color round-trips as absent x-property`() {
        val parsed = roundTrip(regular())
        assertEquals(ScheduledEvent.DEFAULT_COLOR, parsed.colorArgb)
        assertFalse(
            "цвет по умолчанию не должен попадать в ICS",
            eventCalendar(listOf(regular().toIcsEvent())).icsText().contains("X-TNOTE-COLOR"),
        )
    }

    @Test
    fun `daily repeating with until round-trips`() {
        val source = repeating(anchor = "2026-01-01", unit = RepeatUnit.DAYS, interval = 3, until = "2026-01-28")
        val parsed = roundTrip(source)
        assertEquals(source.copy(position = 0, id = 0), parsed)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-01-28")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-31")))
    }

    @Test
    fun `unlimited repeating round-trips without until`() {
        val source = repeating(anchor = "2026-01-01", unit = RepeatUnit.DAYS, interval = 3, until = null)
        val parsed = roundTrip(source)
        assertEquals(source.copy(position = 0, id = 0), parsed)
        assertTrue(parsed.occursOn(LocalDate.parse("2030-01-04")))
    }

    @Test
    fun `weekly with weekdays round-trips`() {
        // Наш аналог «повторять по дням недели» из Яндекса.
        val source = repeating(
            anchor = "2026-01-06",
            unit = RepeatUnit.WEEKS,
            interval = 2,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            until = "2026-03-31",
        )
        val parsed = roundTrip(source)
        assertEquals(source.copy(position = 0, id = 0), parsed)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-01-19"))) // вторая неделя, понедельник
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-20"))) // вторник — не выбран
    }

    @Test
    fun `monthly and yearly repeating round-trip`() {
        val monthly = repeating(anchor = "2026-01-10", unit = RepeatUnit.MONTHS, interval = 2)
        assertEquals(monthly.copy(position = 0, id = 0), roundTrip(monthly))
        val yearly = repeating(anchor = "2026-01-10", unit = RepeatUnit.YEARS, interval = 1)
        assertEquals(yearly.copy(position = 0, id = 0), roundTrip(yearly))
    }

    @Test
    fun `broken repeating without unit exports as plain note`() {
        // OccursOn для такого события всегда false, RRULE на экспорт не пишем.
        val parsed = roundTrip(repeating(unit = null))
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertNull(parsed.repeatUnit)
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-01")))
    }

    @Test
    fun `birthday keeps full anchor date`() {
        val parsed = roundTrip(birthday())
        assertEquals(birthday().copy(position = 0, id = 0), parsed)
        assertEquals("1988-08-09", parsed.date)
    }

    @Test
    fun `weekend marker is never exported as weekend`() {
        // WEEKEND — местная сгенерированная отметка: наружу — как обычное.
        val weekend = regular().copy(type = ScheduledEventType.WEEKEND)
        val parsed = roundTrip(weekend)
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
    }

    // Приём чужих VEVENT (созданы вне T-Note)

    /** Текст календаря с одним VEVENT: RFC-строгие CRLF, без складок линий. */
    private fun foreign(vararg lines: String): ScheduledEvent? {
        val body = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Yandex//Calendar//EN",
            "BEGIN:VEVENT",
        ).plus(lines).plus(listOf("END:VEVENT", "END:VCALENDAR"))
            .joinToString("\r\n") + "\r\n"
        val calendar = parseIcs(body) ?: error("ICS-текст не разобрался:\n$body")
        return calendar.vevents()[0].toScheduledEvent()
    }

    @Test
    fun `foreign plain vevent parses as regular all-day`() {
        val parsed = foreign(
            "UID:foreign-1@example.com",
            "DTSTART;VALUE=DATE:20261117",
            "DTEND;VALUE=DATE:20261118",
            "SUMMARY:Поездка в Казань",
        ) ?: error("VEVENT не распознан")
        assertEquals("foreign-1@example.com", parsed.uid)
        assertEquals("2026-11-17", parsed.date)
        assertEquals("Поездка в Казань", parsed.title)
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertEquals(EventIcon.NOTE, parsed.icon)
    }

    @Test
    fun `foreign timed vevent loses time of day`() {
        val parsed = foreign(
            "UID:foreign-2@example.com",
            "DTSTART:20261117T090000Z",
            "DTEND:20261117T100000Z",
            "SUMMARY:Созвон",
        ) ?: error("VEVENT не распознан")
        assertEquals("2026-11-17", parsed.date)
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
    }

    @Test
    fun `foreign daily rrule becomes repeating with until from count`() {
        val parsed = foreign(
            "UID:foreign-3@example.com",
            "DTSTART;VALUE=DATE:20260101",
            "RRULE:FREQ=DAILY;INTERVAL=3;COUNT=10",
            "SUMMARY:Зарядка",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.DAYS, parsed.repeatUnit)
        assertEquals(3, parsed.repeatInterval)
        // 10-е вхождение: 1, 4, 7, …, 28 января.
        assertEquals("2026-01-28", parsed.repeatUntil)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-01-28")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-31")))
    }

    @Test
    fun `foreign daily rrule with until keeps the boundary date`() {
        // Вхождения: якорь + каждые 3 дня до (и включая) 2026-01-10
        // -> 1, 4, 7, 10 января.
        val parsed = foreign(
            "UID:foreign-4@example.com",
            "DTSTART;VALUE=DATE:20260101",
            "RRULE:FREQ=DAILY;INTERVAL=3;UNTIL=20260110",
            "SUMMARY:Курс быстрой ходьбы",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.DAYS, parsed.repeatUnit)
        assertEquals(3, parsed.repeatInterval)
        assertEquals("2026-01-10", parsed.repeatUntil)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-01-10")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-13")))
    }

    @Test
    fun `foreign yearly rrule becomes repeating years, not birthday`() {
        // Чужое «каждый год» — не день рождения: без подарка-иконки и без
        // сдвига 29 февраля (это семантика людей, а не дат).
        val parsed = foreign(
            "UID:foreign-5@example.com",
            "DTSTART;VALUE=DATE:19880809",
            "RRULE:FREQ=YEARLY",
            "SUMMARY:Годовщина",
        ) ?: error("VEVENT не распознан")
        assertEquals("1988-08-09", parsed.date)
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.YEARS, parsed.repeatUnit)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-08-09")))
    }

    @Test
    fun `foreign yandex birthday converts to birthday type`() {
        // Живая проба (файл-отчёт 2026-11): «день рождения» веб-Яндекса —
        // годовой VEVENT с 🎂 в начале имени и селекторами BYMONTH/
        // BYMONTHDAY ровно по якорю. Наш BIRTHDAY — тот же вечный годовой
        // круг от месяца/дня якоря, по этому признаку и конвертируем.
        val parsed = foreign(
            "UID:93k80elgb93zi19oci2yandex.ru",
            "DTSTART;VALUE=DATE:20261112",
            "DTEND;VALUE=DATE:20261113",
            "SUMMARY:\uD83C\uDF82 День рождения",
            "URL:https://calendar.yandex.ru/event?event_id=442950257772385",
            "RRULE:FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=12;INTERVAL=1",
            "TRANSP:TRANSPARENT",
            "CATEGORIES:T-Note",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.BIRTHDAY, parsed.type)
        assertEquals("День рождения", parsed.title)
        assertEquals("2026-11-12", parsed.date)
        // Правило у BIRTHDAY — часть типа: поля повтора чистые.
        assertNull(parsed.repeatUnit)
        assertEquals(1, parsed.repeatInterval)
        assertTrue(parsed.repeatWeekdays.isEmpty())
        assertNull(parsed.repeatUntil)
        // И круг годовой: в 2028-м вхождение — 12 ноября (модель дней
        // рождения считает по месяцу/дню якоря, occursOn не участвует).
        assertEquals(11 to 12, ScheduledEvent.effectiveBirthdayDate(LocalDate.parse("2026-11-12"), 2028))
    }

    @Test
    fun `foreign yearly selector duplicates of anchor become repeating`() {
        // Любое годовое событие веб-Яндекса (не только дни рождения) идёт
        // селекторами-дубликатами якоря: по набору вхождений это голый
        // YEARLY — представимо, пусть будет REPEATING/YEARS с чистым именем.
        val parsed = foreign(
            "UID:foreign-12@example.com",
            "DTSTART;VALUE=DATE:20261112",
            "DTEND;VALUE=DATE:20261113",
            "SUMMARY:Обзор года",
            "RRULE:FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=12;INTERVAL=1",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.YEARS, parsed.repeatUnit)
        assertEquals("Обзор года", parsed.title)
        assertNull(parsed.repeatUntil)
        assertTrue(parsed.occursOn(LocalDate.parse("2031-11-12")))
    }

    @Test
    fun `foreign yearly selectors off anchor fall back to anchor day`() {
        // BYMONTHDAY=20 при якоре 12-го — селекторы не дублируют якорь:
        // такую периодичность модель не выражает, правило отбрасываем.
        val parsed = foreign(
            "UID:foreign-13@example.com",
            "DTSTART;VALUE=DATE:20261112",
            "DTEND;VALUE=DATE:20261113",
            "SUMMARY:Чужой шаблон",
            "RRULE:FREQ=YEARLY;BYMONTH=11;BYMONTHDAY=20;INTERVAL=1",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatUnit)
    }

    @Test
    fun `birthday icon with count stays repeating with until`() {
        // Ограниченное годовое повторение — не наш BIRTHDAY (тот бессрочный):
        // остаёмся в REPEATING/YEARS с датой последнего вхождения, и имя
        // не конвертируем — иконка оставалась набранной автором.
        val parsed = foreign(
            "UID:foreign-14@example.com",
            "DTSTART;VALUE=DATE:20261112",
            "DTEND;VALUE=DATE:20261113",
            "SUMMARY:\uD83C\uDF82 Юбилей",
            "RRULE:FREQ=YEARLY;COUNT=3;BYMONTH=11;BYMONTHDAY=12;INTERVAL=1",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.YEARS, parsed.repeatUnit)
        assertEquals("\uD83C\uDF82 Юбилей", parsed.title)
        assertEquals("2028-11-12", parsed.repeatUntil)
    }

    @Test
    fun `birthday summary export carries cake icon once`() {
        // Наружу имя дня рождения уходит в форме Яндекса; уже-иконка в
        // имени не дублируется.
        val plain = eventCalendar(listOf(birthday().toIcsEvent())).icsText()
        assertTrue(plain.contains("SUMMARY:\uD83C\uDF82 ДР Маши\r\n"))
        val preIconted = eventCalendar(
            listOf(birthday(title = "\uD83C\uDF82 ДР Маши").toIcsEvent()),
        ).icsText()
        assertTrue(preIconted.contains("SUMMARY:\uD83C\uDF82 ДР Маши\r\n"))
        assertFalse(preIconted.contains("\uD83C\uDF82 \uD83C\uDF82"))
    }

    @Test
    fun `foreign weekly byday becomes repeating with weekdays`() {
        // Главный кейс Яндекса: «повторять по дням недели» — Пн и Ср.
        val parsed = foreign(
            "UID:foreign-6@example.com",
            "DTSTART;VALUE=DATE:20261117", // вторник
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
            "SUMMARY:Тренировка",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.WEEKS, parsed.repeatUnit)
        assertEquals(1, parsed.repeatInterval)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), parsed.repeatWeekdays)
        // Якорь — вхождение всегда, даже когда вторник не выбран (RFC 5545).
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-17")))
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-18"))) // среда
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-23"))) // понедельник
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-24"))) // вторник следующей недели
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-19"))) // четверг
    }

    @Test
    fun `foreign weekly byday with interval two honors week alignment`() {
        // «Каждые 2 недели по понедельникам»: неделя якоря — вхождение,
        // следующая — пропуск.
        val parsed = foreign(
            "UID:foreign-7@example.com",
            "DTSTART;VALUE=DATE:20261116", // понедельник
            "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO",
            "SUMMARY:Полив цветов",
        ) ?: error("VEVENT не распознан")
        assertEquals(RepeatUnit.WEEKS, parsed.repeatUnit)
        assertEquals(2, parsed.repeatInterval)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-30")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-23")))
    }

    @Test
    fun `foreign weekly without byday repeats on anchor weekday`() {
        val parsed = foreign(
            "UID:foreign-8@example.com",
            "DTSTART;VALUE=DATE:20261117", // вторник
            "RRULE:FREQ=WEEKLY",
            "SUMMARY:Созвон",
        ) ?: error("VEVENT не распознан")
        assertEquals(RepeatUnit.WEEKS, parsed.repeatUnit)
        assertEquals(emptySet<DayOfWeek>(), parsed.repeatWeekdays)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-24")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-18")))
    }

    @Test
    fun `foreign monthly rrule becomes repeating months`() {
        val parsed = foreign(
            "UID:foreign-9@example.com",
            "DTSTART;VALUE=DATE:20260110",
            "RRULE:FREQ=MONTHLY;INTERVAL=2",
            "SUMMARY:Плановый визит",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.MONTHS, parsed.repeatUnit)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-03-10")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-02-10")))
    }

    @Test
    fun `foreign rrule with positional byday falls back to anchor day`() {
        // «Вторый вторник месяца» нашей моделью не выразить: якорный день
        // остаётся, остальное не выдумываем.
        val parsed = foreign(
            "UID:foreign-10@example.com",
            "DTSTART;VALUE=DATE:20261110",
            "RRULE:FREQ=MONTHLY;BYDAY=2TU",
            "SUMMARY:Встреча клуба",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatUnit)
    }

    @Test
    fun `foreign rrule with bymonthday falls back to anchor day`() {
        val parsed = foreign(
            "UID:foreign-11@example.com",
            "DTSTART;VALUE=DATE:20261110",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=10,15",
            "SUMMARY:Два числа",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatUnit)
    }

    // Отменённые вхождения и метки времени

    @Test
    fun `exdates round-trip through ics text`() {
        // Наш аналог «повторять по дням недели» с парой отменённых дней.
        val source = repeating(
            anchor = "2026-11-16", // понедельник
            unit = RepeatUnit.WEEKS,
            interval = 1,
            weekdays = setOf(DayOfWeek.MONDAY),
        ).copy(exdates = setOf(LocalDate.parse("2026-11-30"), LocalDate.parse("2026-12-14")))
        val parsed = roundTrip(source)
        assertEquals(source.copy(position = 0, id = 0), parsed)
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-30")))
        assertTrue(parsed.occursOn(LocalDate.parse("2026-12-07")))
    }

    @Test
    fun `foreign exdate removes occurrences`() {
        // «По понедельникам, но 30 ноября не проводить» — ровно то,
        // что делает Яндекс при отмене одного повтора.
        val parsed = foreign(
            "UID:foreign-14@example.com",
            "DTSTART;VALUE=DATE:20261116", // понедельник
            "RRULE:FREQ=WEEKLY;BYDAY=MO",
            "EXDATE;VALUE=DATE:20261130",
            "SUMMARY:Отряд",
        ) ?: error("VEVENT не распознан")
        assertEquals(setOf(LocalDate.parse("2026-11-30")), parsed.exdates)
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-30")))
        assertTrue(parsed.occursOn(LocalDate.parse("2026-12-07")))
    }

    @Test
    fun `foreign multi-day event becomes daily repeat until last day`() {
        // Отпуск 1–5 марта: DTEND эксклюзивен, значит последний видимый
        // день — 5-е. Визуально это «каждый день до 5 марта» — готовая
        // механика частоты DAYS, отдельное поле endDate не нужно.
        val parsed = foreign(
            "UID:foreign-15@example.com",
            "DTSTART;VALUE=DATE:20260301",
            "DTEND;VALUE=DATE:20260306",
            "SUMMARY:Отпуск",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.DAYS, parsed.repeatUnit)
        assertEquals(1, parsed.repeatInterval)
        assertEquals("2026-03-05", parsed.repeatUntil)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-03-01")))
        assertTrue(parsed.occursOn(LocalDate.parse("2026-03-05")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-03-06")))
    }

    @Test
    fun `foreign single-day event keeps regular type`() {
        // Однодневное событие имеет DTEND = якорь + 1 — адаптер не включается.
        val parsed = foreign(
            "UID:foreign-16@example.com",
            "DTSTART;VALUE=DATE:20260301",
            "DTEND;VALUE=DATE:20260302",
            "SUMMARY:Один день",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatUnit)
    }

    @Test
    fun `timestamps come from created and last-modified`() {
        val created = Instant.parse("2020-05-01T10:30:00Z").toEpochMilli()
        val modified = Instant.parse("2026-02-20T08:00:00Z").toEpochMilli()
        val parsed = foreign(
            "UID:foreign-17@example.com",
            "DTSTART;VALUE=DATE:20261117",
            "CREATED:20200501T103000Z",
            "LAST-MODIFIED:20260220T080000Z",
            "SUMMARY:С метками",
        ) ?: error("VEVENT не распознан")
        assertEquals(created, parsed.createdAt)
        assertEquals(modified, parsed.modifiedAt)
    }

    @Test
    fun `missing created falls back to dtstamp`() {
        // DTSTAMP обязателен по RFC — он закрывает пропуски CREATED и
        // LAST-MODIFIED (это поведение Яндекса в том числе).
        val parsed = foreign(
            "UID:foreign-18@example.com",
            "DTSTART;VALUE=DATE:20261117",
            "DTSTAMP:20260305T120000Z",
            "SUMMARY:Только штамп",
        ) ?: error("VEVENT не распознан")
        val stamp = Instant.parse("2026-03-05T12:00:00Z").toEpochMilli()
        assertEquals(stamp, parsed.createdAt)
        assertEquals(stamp, parsed.modifiedAt)
    }

    @Test
    fun `own timestamps round-trip through ics text`() {
        val source = regular().copy(
            createdAt = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(),
            modifiedAt = Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(),
        )
        val parsed = roundTrip(source)
        assertEquals(source.copy(position = 0, id = 0), parsed)
    }

    // Непригодное на входе

    @Test
    fun `vevent without dtstart is rejected`() {
        val parsed = foreign(
            "UID:foreign-12@example.com",
            "SUMMARY:Без даты",
        )
        assertNull(parsed)
    }

    @Test
    fun `vevent without uid is rejected`() {
        val parsed = foreign(
            "DTSTART;VALUE=DATE:20261117",
            "SUMMARY:Без UID",
        )
        assertNull(parsed)
    }

    @Test
    fun `vevent with broken dtstart is rejected`() {
        val body = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Yandex//Calendar//EN",
            "BEGIN:VEVENT",
            "UID:foreign-13@example.com",
            "DTSTART;VALUE=DATE:31.02.2026",
            "SUMMARY:Битая дата",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n") + "\r\n"
        // Битая дата может отвергнуться на любом из двух барьеров:
        // парсером ICS (null-календарь) или разбором DTSTART (null-событие).
        val calendar = parseIcs(body)
        assertNull(calendar?.vevents()?.firstOrNull()?.toScheduledEvent())
    }
}
