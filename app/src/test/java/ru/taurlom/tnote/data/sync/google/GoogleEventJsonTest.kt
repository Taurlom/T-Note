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
import java.time.OffsetDateTime
import java.time.ZoneId

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
    fun `timed google event keeps local clock`() {
        // Момент приводится к зоне устройства: встреча показывается по
        // местным часам, а не по часам сервера.
        val event = googleJson(
            "id" to "11111111-2222-3333-4444-555555555556",
            "status" to "confirmed",
            "summary" to "Созвон",
            "start" to JSONObject().put("dateTime", "2026-11-20T09:00:00+03:00"),
            "end" to JSONObject().put("dateTime", "2026-11-20T10:00:00+03:00"),
        )
        val parsed = googleEventToVEvent(event)?.toScheduledEvent() ?: error("VEVENT не распознан")
        val local = OffsetDateTime.parse("2026-11-20T09:00:00+03:00")
            .atZoneSameInstant(ZoneId.systemDefault())
        assertEquals(local.hour * 60 + local.minute, parsed.startMinutes)
        val localEnd = OffsetDateTime.parse("2026-11-20T10:00:00+03:00")
            .atZoneSameInstant(ZoneId.systemDefault())
        assertEquals(localEnd.hour * 60 + localEnd.minute, parsed.endMinutes)
    }

    @Test
    fun `timed multiday google event keeps its span`() {
        // Отпуск с пн по ср с точным временем: конец «среда 17:00» —
        // конец приходит на третий день, событие остаётся обычным
        // (не повтором) с датой конца в endDate: один объект — один UID.
        val event = googleJson(
            "id" to "11111111-2222-3333-4444-555555555557",
            "status" to "confirmed",
            "summary" to "Отпуск",
            "start" to JSONObject().put("dateTime", "2026-11-16T09:00:00+03:00"),
            "end" to JSONObject().put("dateTime", "2026-11-18T17:00:00+03:00"),
        )
        val parsed = googleEventToVEvent(event)?.toScheduledEvent() ?: error("VEVENT не распознан")
        assertEquals(ScheduledEventType.REGULAR, parsed.type)
        assertEquals("2026-11-16", parsed.date)
        assertEquals("2026-11-18", parsed.endDate)
        assertNull(parsed.repeatUnit)
        assertNull(parsed.repeatUntil)
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
    fun `timed exdate lines survive the google import`() {
        // С 11-го бага экспорт пишет исключения timed-серий с временем —
        // обратный ход обязан их понимать: и «naive», и UTC-форму. День
        // терялся бы молча, если разбор ожидал только DATE.
        val parsed = googleEventToVEvent(
            googleJson(
                "id" to "11111111-2222-3333-4444-555555555555",
                "status" to "confirmed",
                "summary" to "Зарядка",
                "start" to JSONObject().put("dateTime", "2026-11-16T10:00:00+03:00"),
                "end" to JSONObject().put("dateTime", "2026-11-16T11:00:00+03:00"),
                "recurrence" to org.json.JSONArray()
                    .put("RRULE:FREQ=WEEKLY;BYDAY=MO")
                    .put("EXDATE:20261130T100000")
                    .put("EXDATE;TZID=Europe/Moscow:20261207T100000"),
            ),
        )?.toScheduledEvent() ?: error("VEVENT не распознан")
        assertFalse(parsed.occursOn(LocalDate.parse("2026-11-30")))
        assertFalse(parsed.occursOn(LocalDate.parse("2026-12-07")))
        assertTrue(parsed.occursOn(LocalDate.parse("2026-12-14")))
    }

    @Test
    fun `own timed series goes to google with datetime until and exdate`() {
        // На обратном ходе та же форма: RRULE с UNTIL конца дня (плавающим)
        // и EXDATE с моментом вхождения — Google так описывает timed-повторы.
        val source = ScheduledEvent(
            uid = "11111111-2222-3333-4444-555555555555",
            date = "2026-11-16",
            title = "Зарядка",
            type = ScheduledEventType.REPEATING,
            repeatUnit = RepeatUnit.WEEKS,
            repeatInterval = 1,
            repeatWeekdays = setOf(java.time.DayOfWeek.MONDAY),
            repeatUntil = "2026-12-28",
            startMinutes = 600,
            endMinutes = 660,
            exdates = setOf(LocalDate.parse("2026-11-30")),
        )
        val vevent = parseIcs(eventCalendar(listOf(source.toIcsEvent())).icsText())
            ?.vevents()?.single() ?: error("ICS не разобрался")
        val recurrence = veventToGoogleJson(vevent).getJSONArray("recurrence")
        val lines = (0 until recurrence.length()).map { index -> recurrence.getString(index) }
        assertTrue(lines.any { it.startsWith("RRULE:") && it.contains("UNTIL=20261228T235959") })
        assertTrue(lines.any { it.startsWith("EXDATE") && it.contains("20261130T100000") })
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
    fun `timed event goes to google as date time`() {
        // Событие с часами едет в Google как dateTime с местным смещением —
        // так его понимает календарь; вседневное остаётся датой.
        val source = ScheduledEvent(
            uid = "11111111-2222-3333-4444-555555555555",
            date = "2026-02-17",
            title = "Ремонт",
            type = ScheduledEventType.REGULAR,
            startMinutes = 10 * 60,
            endMinutes = 11 * 60,
        )
        val ics = eventCalendar(listOf(source.toIcsEvent())).icsText()
        val vevent = parseIcs(ics)?.vevents()?.single() ?: error("ICS не разобрался")
        val json = veventToGoogleJson(vevent)
        val start = json.getJSONObject("start")
        assertFalse("у события со временем нет формы «date»", start.has("date"))
        val localStart = OffsetDateTime.parse(start.getString("dateTime"))
            .atZoneSameInstant(ZoneId.systemDefault())
        assertEquals(10, localStart.hour)
        assertEquals(0, localStart.minute)
        val localEnd = OffsetDateTime.parse(json.getJSONObject("end").getString("dateTime"))
            .atZoneSameInstant(ZoneId.systemDefault())
        assertEquals(11, localEnd.hour)
        assertEquals(0, localEnd.minute)
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

    // Временная защита: PUT отправляет полный ресурс, поэтому события с
    // полями вне модели и вхождения серий движок не перезаписывает.

    @Test
    fun `series exception carries its parent id`() {
        val exception = googleJson(
            "id" to "2g5qcork5i00000000",
            "recurringEventId" to "11111111-2222-3333-4444-555555555555",
            "originalStartTime" to dayJson("2026-11-21"),
            "status" to "cancelled",
        )
        assertEquals("11111111-2222-3333-4444-555555555555", exception.seriesExceptionParentId())

        assertNull(allDayEvent().seriesExceptionParentId())
        assertNull(googleJson("id" to "x").seriesExceptionParentId())
    }

    @Test
    fun `google fields outside the model block rewriting`() {
        val extras = listOf<Pair<String, Any>>(
            "location" to "Офис 304",
            "attendees" to org.json.JSONArray().put(org.json.JSONObject().put("email", "lena@example.com")),
            "reminders" to org.json.JSONObject()
                .put("useDefault", false)
                .put("overrides", org.json.JSONArray().put(org.json.JSONObject().put("method", "popup").put("minutes", 10))),
            "conferenceData" to org.json.JSONObject().put("conferenceId", "abc"),
            "source" to org.json.JSONObject().put("title", "Письмо"),
            "colorId" to "7",
            "visibility" to "private",
            "transparency" to "transparent",
            "eventType" to "outOfOffice",
            "extendedProperties" to org.json.JSONObject().put("private", org.json.JSONObject().put("x", "1")),
            "recurrence" to org.json.JSONArray().put("RDATE:20261125"),
            "guestsCanModify" to true,
            "guestsCanInviteOthers" to false,
        )
        extras.forEach { (key, value) ->
            val json = googleJson("id" to "x", "summary" to "Встреча", key to value)
            assertTrue("«$key» — данные вне модели", json.hasUnmodeledContent())
        }
    }

    @Test
    fun `plain google event stays writable`() {
        // Дефолты Google не отдаёт, а если отдаст — они ничего не значат.
        assertFalse(allDayEvent().hasUnmodeledContent())
        assertFalse(
            googleJson(
                "id" to "x",
                "visibility" to "default",
                "transparency" to "opaque",
                "eventType" to "legacy",
                "recurrence" to org.json.JSONArray().put("RRULE:FREQ=DAILY;COUNT=5").put("EXDATE:20261125"),
            ).hasUnmodeledContent(),
        )
        // Наш собственный экспорт обязан оставаться записываемым.
        val own = veventToGoogleJson(
            ScheduledEvent(
                date = "2026-11-20",
                title = "Своё",
                type = ScheduledEventType.REPEATING,
                repeatUnit = RepeatUnit.DAYS,
                icon = EventIcon.BEACH,
            ).toIcsEvent(),
        )
        assertFalse(own.hasUnmodeledContent())
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
