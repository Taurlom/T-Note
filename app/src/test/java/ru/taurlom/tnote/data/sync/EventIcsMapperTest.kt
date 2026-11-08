package ru.taurlom.tnote.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
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

    private fun repeating(anchor: String = "2026-01-01", interval: Int? = 3, repeatDays: Int = 0) = ScheduledEvent(
        uid = "aaaaaaa1-0000-0000-0000-000000000001",
        date = anchor,
        title = "Зарядка",
        type = ScheduledEventType.REPEATING,
        repeatIntervalDays = interval,
        repeatDays = repeatDays,
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
        return events[0].toScheduledEvent() ?: error("VEVENT не распознан: $ics")
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
    fun `repeating with window round-trips`() {
        val source = repeating(repeatDays = 10)
        val parsed = roundTrip(source)
        assertEquals(source.copy(position = 0, id = 0), parsed)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-01-10")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-11")))
    }

    @Test
    fun `unlimited repeating round-trips without count`() {
        val parsed = roundTrip(repeating(repeatDays = 0))
        assertEquals(repeating(repeatDays = 0).copy(position = 0, id = 0), parsed)
        assertTrue(parsed.occursOn(LocalDate.parse("2030-01-04")))
    }

    @Test
    fun `broken repeating without interval exports as plain note`() {
        // OccursOn для такого события всегда false, RRULE на экспорт не пишем.
        val parsed = roundTrip(repeating(interval = null, repeatDays = 7))
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatIntervalDays)
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
    fun `foreign daily rrule becomes repeating with count`() {
        val parsed = foreign(
            "UID:foreign-3@example.com",
            "DTSTART;VALUE=DATE:20260101",
            "RRULE:FREQ=DAILY;INTERVAL=3;COUNT=10",
            "SUMMARY:Зарядка",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(3, parsed.repeatIntervalDays)
        assertEquals(10, parsed.repeatDays)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-01-10")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-01-11")))
    }

    @Test
    fun `foreign daily rrule with until is converted to count`() {
        // Вхождения: якорь + каждые 3 дня до (и включая) 2026-01-10
        // -> 1, 4, 7, 10 января = 4 вхождения.
        val parsed = foreign(
            "UID:foreign-4@example.com",
            "DTSTART;VALUE=DATE:20260101",
            "RRULE:FREQ=DAILY;INTERVAL=3;UNTIL=20260110",
            "SUMMARY:Курс быстрой ходьбы",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(3, parsed.repeatIntervalDays)
        assertEquals(4, parsed.repeatDays)
    }

    @Test
    fun `foreign yearly rrule is treated as birthday`() {
        val parsed = foreign(
            "UID:foreign-5@example.com",
            "DTSTART;VALUE=DATE:19880809",
            "RRULE:FREQ=YEARLY",
            "SUMMARY:Годовщина",
        ) ?: error("VEVENT не распознан")
        assertEquals("1988-08-09", parsed.date)
        assertEquals(ScheduledEventType.BIRTHDAY, parsed.type)
    }

    @Test
    fun `foreign weekly rrule falls back to plain anchor day`() {
        // WEEKLY на нашей модели не выразить: пока — якорный день, а честное
        // разворачивание вхождений делает движок этапа 1.
        val parsed = foreign(
            "UID:foreign-6@example.com",
            "DTSTART;VALUE=DATE:20261117",
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
            "SUMMARY:Тренировка",
        ) ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatIntervalDays)
        assertEquals(0, parsed.repeatDays)
    }

    // Непригодное на входе

    @Test
    fun `vevent without dtstart is rejected`() {
        val parsed = foreign(
            "UID:no-dtstart@example.com",
            "SUMMARY:Пустышка",
        )
        assertNull(parsed)
    }

    @Test
    fun `garbage ics text parses to null`() {
        assertNull(parseIcs("это не календарь"))
    }

    // Живой вид сервера (пробы CalDAV Яндекса)

    /**
     * Яндекс оборачивает объект в VCALENDAR с VTIMEZONE (Europe/Moscow с
     * историческими DST-переходами) и служебными свойствами: парсер должен
     * игнорировать DTSTART'ы таймзоны и вытащить только VEVENT.
     */
    @Test
    fun `yandex-style calendar with VTIMEZONE parses the event, not the timezone`() {
        val ics = listOf(
            "BEGIN:VCALENDAR",
            "PRODID:-//Yandex LLC//Yandex Calendar//EN",
            "VERSION:2.0",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "BEGIN:VTIMEZONE",
            "TZID:Europe/Moscow",
            "BEGIN:STANDARD",
            "DTSTART:18800101T000000",
            "TZOFFSETFROM:+023017",
            "TZOFFSETTO:+023017",
            "TZNAME:MMT",
            "END:STANDARD",
            "BEGIN:DAYLIGHT",
            "DTSTART:20241110T233000",
            "TZOFFSETFROM:+0300",
            "TZOFFSETTO:+0300",
            "TZNAME:MSK",
            "END:DAYLIGHT",
            "END:VTIMEZONE",
            "BEGIN:VEVENT",
            "UID:probe-1@tnote.local",
            "DTSTAMP:20261108T190135Z",
            "DTSTART;VALUE=DATE:20261117",
            "DTEND;VALUE=DATE:20261118",
            "SUMMARY:GET-проверка",
            "URL:https://calendar.yandex.ru/event?event_id=4413021",
            "TRANSP:OPAQUE",
            "CATEGORIES:T-Note",
            "CLASS:PRIVATE",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n") + "\r\n"

        val parsed = parseIcs(ics)?.vevents()?.firstOrNull()?.toScheduledEvent()

        // Единственное событие; DTSTART таймзоны (1880 год) не спутался с ним.
        assertEquals("2026-11-17", parsed?.date)
        assertEquals("GET-проверка", parsed?.title)
        assertEquals(ScheduledEventType.REGULAR, parsed?.type)
        assertEquals(EventIcon.NOTE, parsed?.icon)
    }

    /** Таймзонное время веб-интерфейса Яндекса: дата дня остаётся датой. */
    @Test
    fun `timed event with TZID keeps its local date`() {
        val ics = listOf(
            "BEGIN:VCALENDAR",
            "PRODID:-//Yandex LLC//Yandex Calendar//EN",
            "VERSION:2.0",
            "BEGIN:VTIMEZONE",
            "TZID:Europe/Moscow",
            "BEGIN:STANDARD",
            "DTSTART:19701025T030000",
            "TZOFFSETFROM:+0300",
            "TZOFFSETTO:+0200",
            "TZNAME:MSK",
            "END:STANDARD",
            "END:VTIMEZONE",
            "BEGIN:VEVENT",
            "UID:probe-tz@tnote.local",
            "DTSTAMP:20261108T120000Z",
            "DTSTART;TZID=Europe/Moscow:20261117T090000",
            "DTEND;TZID=Europe/Moscow:20261117T100000",
            "SUMMARY:Таймзонное",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n") + "\r\n"

        val parsed = parseIcs(ics)?.vevents()?.firstOrNull()?.toScheduledEvent()

        assertEquals("2026-11-17", parsed?.date)
        assertEquals("Таймзонное", parsed?.title)
        assertEquals(ScheduledEventType.REGULAR, parsed?.type)
    }
}
