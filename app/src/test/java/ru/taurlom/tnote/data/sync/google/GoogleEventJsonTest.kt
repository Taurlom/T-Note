package ru.taurlom.tnote.data.sync.google

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.data.sync.eventCalendar
import ru.taurlom.tnote.data.sync.hasTnoteMetadata
import ru.taurlom.tnote.data.sync.icsText
import ru.taurlom.tnote.data.sync.parseIcs
import ru.taurlom.tnote.data.sync.toIcsEvent
import ru.taurlom.tnote.data.sync.toScheduledEvent
import ru.taurlom.tnote.data.sync.vevents
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.time.LocalDate

/**
 * Контракт конвертеров «Google Calendar API v3 ↔ VEVENT»: JSON события
 * Google разбирается в модель через общий toScheduledEvent (никакой второй
 * копии правил RRULE), а наш экспорт превращается в JSON Google.
 */
class GoogleEventJsonTest {

    /** Событие Google «весь день» из живой формы API. */
    private fun googleJson(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    private fun allDayEvent(
        id: String = "11111111-2222-3333-4444-555555555555",
        summary: String = "Из Google",
        description: String? = null,
        start: String = "2026-11-20",
        end: String = "2026-11-21",
        recurrence: List<String>? = null,
    ): JSONObject = googleJson(
        "id" to id,
        "status" to "confirmed",
        "summary" to summary,
        "start" to dayJson(start),
        "end" to dayJson(end),
        "created" to "2026-11-10T10:00:00.000Z",
        "updated" to "2026-11-11T11:30:00.000Z",
        "description" to description,
        "recurrence" to recurrence?.let { org.json.JSONArray(it) },
    )

    /** Вложенное поле «дата»: org.json Map не оборачивает — только явный JSONObject. */
    private fun dayJson(value: String): JSONObject = JSONObject().put("date", value)

    @Test
    fun `all day google event parses to regular day marker`() {
        val parsed = googleEventToVEvent(allDayEvent())?.toScheduledEvent()
            ?: error("VEVENT не распознан")
        assertEquals("11111111-2222-3333-4444-555555555555", parsed.uid)
        assertEquals("2026-11-20", parsed.date)
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        // Метка правки — из updated (created Google проставляет сам).
        assertEquals(java.time.Instant.parse("2026-11-11T11:30:00Z").toEpochMilli(), parsed.modifiedAt)
    }

    @Test
    fun `marker in description restores icon and color`() {
        val vevent = googleEventToVEvent(allDayEvent(description = "[T-Note] icon=TRAVEL color=#FFE53935"))
            ?: error("VEVENT не распознан")
        val parsed = vevent.toScheduledEvent() ?: error("VEVENT не распознан")
        assertEquals(EventIcon.TRAVEL, parsed.icon)
        assertEquals(0xFFE53935L, parsed.colorArgb)
        assertTrue("маркер считается нашими метаданными", vevent.hasTnoteMetadata())
    }

    @Test
    fun `timed google event maps to its calendar day`() {
        val event = googleJson(
            "id" to "11111111-2222-3333-4444-555555555556",
            "status" to "confirmed",
            "summary" to "Созвон",
            "start" to JSONObject().put("dateTime", "2026-11-20T09:00:00+03:00"),
            "end" to JSONObject().put("dateTime", "2026-11-20T10:00:00+03:00"),
        )
        val parsed = googleEventToVEvent(event)?.toScheduledEvent() ?: error("VEVENT не распознан")
        assertEquals("2026-11-20", parsed.date)
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertNull(parsed.repeatUnit)
    }

    @Test
    fun `timed multiday google event covers last day`() {
        // Отпуск с пн по ср с точным временем: конец «среда 17:00» —
        // среда тоже показывается (энд переводится в эксклюзивную дату
        // четверга, разбор −1 день возвращает среду).
        val event = googleJson(
            "id" to "11111111-2222-3333-4444-555555555557",
            "status" to "confirmed",
            "summary" to "Отпуск",
            "start" to JSONObject().put("dateTime", "2026-11-16T09:00:00+03:00"),
            "end" to JSONObject().put("dateTime", "2026-11-18T17:00:00+03:00"),
        )
        val parsed = googleEventToVEvent(event)?.toScheduledEvent() ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.DAYS, parsed.repeatUnit)
        assertEquals("2026-11-18", parsed.repeatUntil)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-18")))
    }

    @Test
    fun `cancelled google event is skipped`() {
        val event = allDayEvent().put("status", "cancelled")
        assertNull(googleEventToVEvent(event))
    }

    @Test
    fun `recurrence rrule becomes repeating with weekdays`() {
        val parsed = googleEventToVEvent(
            allDayEvent(recurrence = listOf("RRULE:FREQ=WEEKLY;BYDAY=MO,WE")),
        )?.toScheduledEvent() ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REPEATING, parsed.type)
        assertEquals(RepeatUnit.WEEKS, parsed.repeatUnit)
        assertEquals(2, parsed.repeatWeekdays.size)
        assertTrue(parsed.occursOn(LocalDate.parse("2026-11-23"))) // понедельник
    }

    @Test
    fun `exdate in recurrence excludes occurrences`() {
        val parsed = googleEventToVEvent(
            allDayEvent(
                start = "2026-11-16", // понедельник
                end = "2026-11-17",
                recurrence = listOf(
                    "RRULE:FREQ=WEEKLY;BYDAY=MO",
                    "EXDATE;VALUE=DATE:20261130",
                ),
            ),
        )?.toScheduledEvent() ?: error("VEVENT не распознан")
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-30")))
        assertTrue(parsed.occursOn(LocalDate.parse("2026-12-07")))
    }

    @Test
    fun `own event round trips through google json`() {
        val source = ScheduledEvent(
            uid = "11111111-2222-3333-4444-555555555555",
            date = "2026-02-17",
            title = "Ремонт",
            type = ScheduledEventType.REPEATING,
            repeatUnit = RepeatUnit.WEEKS,
            repeatWeekdays = setOf(java.time.DayOfWeek.MONDAY),
            icon = EventIcon.BUILD,
            colorArgb = 0xFFE53935L,
            hidePastOccurrences = true,
            createdAt = 1_000_000_000_000L,
            modifiedAt = 1_500_000_000_000L,
        )
        val ics = eventCalendar(listOf(source.toIcsEvent())).icsText()
        val vevent = parseIcs(ics)?.vevents()?.single() ?: error("ICS не разобрался")
        val json = veventToGoogleJson(vevent)
        // UUID с дефисами → base32hex-форма с нашим маркером.
        assertEquals("tnote11111111222233334444555555555555", json.getString("id"))
        assertEquals("Ремонт", json.getString("summary"))
        assertTrue(json.getString("description").startsWith("[T-Note]"))
        assertEquals("2026-02-17", json.getJSONObject("start").getString("date"))
        assertEquals("2026-02-18", json.getJSONObject("end").getString("date"))
        val recurrence = json.getJSONArray("recurrence")
        assertEquals(1, recurrence.length())
        assertTrue(recurrence.getString(0).startsWith("RRULE:"))
        // Обратный ход: JSON Google → VEVENT → модель — те же поля. Метки
        // времени Google ведёт сам (created проставляет при создании,
        // updated меняет при правке), поэтому при сравнении выравниваются.
        val parsed = googleEventToVEvent(json)?.toScheduledEvent() ?: error("JSON не распознан")
        val expected = source.copy(id = 0, position = 0)
        assertEquals(
            expected,
            parsed.copy(id = 0, position = 0, createdAt = expected.createdAt, modifiedAt = expected.modifiedAt),
        )
    }

    @Test
    fun `event ids follow google base32hex rules and restore uids`() {
        // Google допускает id только из строчных a-v и цифр: UUID с дефисами
        // и «@» заметок невозможны — санитизация с обратным восстановлением.
        val cases = listOf(
            // канонический UUID (новые события) — tnote + без дефисов
            "11111111-2222-3333-4444-555555555555",
            // носитель заметки «note-…@…» — tnote + hex-код байтов
            "note-2026-03-05@taurlom",
            // uid из миграции — lower-hex, уже base32hex-совместим
            "4bd53b9bccd78a2facebf9af85533b9b",
            // чужой идентификатор Google — вне нашего алфавита, hex-кодом
            "93k80elgcjxjex0b0y0yandex.ru",
        )
        cases.forEach { uid ->
            val id = googleEventIdFor(uid)
            assertTrue("не base32hex: $id", id.matches(Regex("[0-9a-v]+")))
            assertTrue(id.length in 5..1024)
            assertEquals("не восстановился: $uid", uid, googleUidFromEventId(id))
        }
        assertEquals("tnote11111111222233334444555555555555", googleEventIdFor(cases[0]))
        // Чужой base32hex-совместимый id проходит без маркера.
        assertEquals("4bd53b9bccd78a2facebf9af85533b9b", googleEventIdFor(cases[2]))
    }

    @Test
    fun `resource url splits into calendar and event`() {
        val (calendar, event) = splitResourceUrl("taurlom@gmail.com/11111111-2222")
            ?: error("ссылка должна разбираться")
        assertEquals("taurlom@gmail.com", calendar)
        assertEquals("11111111-2222", event)
        assertNull(splitResourceUrl("без-слэша"))
    }
}
