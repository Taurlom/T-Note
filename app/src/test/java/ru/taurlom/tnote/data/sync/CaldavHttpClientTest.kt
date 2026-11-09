package ru.taurlom.tnote.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Разбор multistatus на ответах в живом виде Яндекса (сняты пробами
 * PROPFIND/REPORT): префиксы D:/C: — лишние, смысл несёт namespace-URI;
 * calendar-data приходит с переносами строк и VTIMEZONE внутри.
 */
@RunWith(RobolectricTestRunner::class)
class CaldavHttpClientTest {

    private val propfindHomeXml = """
        <?xml version='1.0' encoding='utf-8'?>
        <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
        <D:response><D:href>/calendars/taur-lom%40yandex.ru/</D:href>
        <D:propstat><D:prop><D:displayname>Личные</D:displayname></D:prop>
        <D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        <D:response><D:href>/calendars/taur-lom%40yandex.ru/events-441302135063076/</D:href>
        <D:propstat><D:prop><D:displayname>T-Note</D:displayname>
        <D:resourcetype><D:collection/><C:calendar/></D:resourcetype>
        <C:supported-calendar-component-set><C:comp name="VEVENT"/></C:supported-calendar-component-set>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        <D:response><D:href>/calendars/taur-lom%40yandex.ru/todos/</D:href>
        <D:propstat><D:prop><D:displayname>Не забыть</D:displayname>
        <D:resourcetype><D:collection/><C:calendar/></D:resourcetype>
        <C:supported-calendar-component-set><C:comp name="VTODO"/></C:supported-calendar-component-set>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        </D:multistatus>
    """.trimIndent()

    private val reportEventXml = """
        <?xml version='1.0' encoding='utf-8'?>
        <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
        <D:response><D:href>/calendars/taur-lom%40yandex.ru/events-441302135063076/probe-1.ics</D:href>
        <D:propstat><D:prop>
        <D:getetag>"etag-1"</D:getetag>
        <C:calendar-data>BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VTIMEZONE
        TZID:Europe/Moscow
        BEGIN:STANDARD
        DTSTART:18800101T000000
        TZOFFSETFROM:+023017
        TZOFFSETTO:+023017
        TZNAME:MMT
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        UID:probe-1@tnote.local
        DTSTAMP:20261108T190135Z
        DTSTART;VALUE=DATE:20261117
        DTEND;VALUE=DATE:20261118
        SUMMARY:GET-проверка
        END:VEVENT
        END:VCALENDAR</C:calendar-data>
        </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        </D:multistatus>
    """.trimIndent()

    private val propfindPartialXml = """
        <?xml version='1.0' encoding='utf-8'?>
        <D:multistatus xmlns:D="DAV:">
        <D:response><D:href>/x/</D:href>
        <D:propstat><D:prop><D:displayname/></D:prop>
        <D:status>HTTP/1.1 404 Not Found</D:status></D:propstat>
        <D:propstat><D:prop><D:getetag>"e"</D:getetag></D:prop>
        <D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `propfind listing extracts calendars with component support`() {
        val entries = DavXml.parseMultistatus(propfindHomeXml)

        assertEquals(3, entries.size)
        val tnote = entries.first { it.displayName == "T-Note" }
        assertTrue(tnote.isCalendar)
        assertTrue(tnote.supportsVevents)
        assertEquals("/calendars/taur-lom%40yandex.ru/events-441302135063076/", tnote.href)

        // Сборник задач — календарь, но VEVENT не умеет: движок его отфильтрует.
        val todos = entries.first { it.href.endsWith("/todos/") }
        assertTrue(todos.isCalendar)
        assertFalse(todos.supportsVevents)

        // Корень home-set — не календарь-коллекция с событиями.
        val home = entries.first { it.displayName == "Личные" }
        assertFalse(home.isCalendar)
    }

    @Test
    fun `calendar-query extracts etag and full ics body`() {
        val entries = DavXml.parseMultistatus(reportEventXml)

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("\"etag-1\"", entry.etag)
        assertEquals(200, entry.status)
        val data = entry.calendarData.orEmpty()
        assertTrue(data.contains("BEGIN:VEVENT"))
        assertTrue(data.contains("DTSTART;VALUE=DATE:20261117"))
        assertFalse(data.contains("BEGIN:VTODO"))
    }

    @Test
    fun `propstat with error status does not overwrite entry properties`() {
        val entries = DavXml.parseMultistatus(propfindPartialXml)

        assertEquals(1, entries.size)
        assertEquals("/x/", entries[0].href)
        assertEquals(200, entries[0].status)
        assertNull(entries[0].displayName)
        assertEquals("\"e\"", entries[0].etag)
    }

    @Test
    fun `non-dav body yields no entries`() {
        assertEquals(0, DavXml.parseMultistatus("<html><body>404</body></html>").size)
    }

    @Test
    fun `unquoted getetag from yandex is stored quoted`() {
        // Живой ответ Яндекса: getetag — голое число без кавычек.
        val xml = """
            <?xml version='1.0' encoding='utf-8'?>
            <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
            <D:response><D:href>/calendars/x/1.ics</D:href>
            <D:propstat><D:prop>
            <D:getetag>1794258807976</D:getetag>
            </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
            </D:multistatus>
        """.trimIndent()

        assertEquals("\"1794258807976\"", DavXml.parseMultistatus(xml).single().etag)
    }

    @Test
    fun `etag forms are normalized to quoted shape`() {
        // Формы на границах транспорта приводятся к кавычкам: так If-Match
        // всегда уходит в читаемой Яндексом форме (без них — вечный 412).
        assertEquals("\"1794258807976\"", normalizeEtag("1794258807976"))
        assertEquals("\"e\"", normalizeEtag(" \"e\" "))
        assertNull(normalizeEtag(null))
        assertNull(normalizeEtag(""))
        assertNull(normalizeEtag("   "))
    }
}
