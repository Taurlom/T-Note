package ru.taurlom.tnote.data.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.taurlom.tnote.domain.repository.CaldavSyncException

/**
 * Транспорт записи (этап 2) на реальном HTTP-стеке: MockWebServer закрепляет
 * заголовки предусловий RFC 7232 (If-Match / If-None-Match:*), форму ответа
 * (код + ETag) и тип исключения при отказе авторизации. Robolectric нужен,
 * потому что точечный PROPFIND падает в тот же разбор multistatus
 * (DavXml через android.util.Xml), что и полная вычитка.
 */
@RunWith(RobolectricTestRunner::class)
class CaldavWriteTest {

    private lateinit var server: MockWebServer
    private lateinit var client: CaldavHttpClient
    private lateinit var api: CaldavApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CaldavHttpClient()
        api = CaldavClient(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun icsBody(): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//T-Note//iCalendar 1.0//RU
        BEGIN:VEVENT
        UID:test-uid
        DTSTAMP:20261109T200000Z
        DTSTART;VALUE=DATE:20261117
        DTEND;VALUE=DATE:20261118
        SUMMARY:Проверка PUT
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `first publish sends if-none-match star and keeps returned etag`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).addHeader("ETag", "\"fresh-e\""))
        val url = server.url("/calendars/u%40yandex.ru/events-1/test-uid.ics").toString()

        val result = api.putEvent(url, icsBody(), "user", "pass", etag = null, createIfAbsent = true)

        assertEquals(201, result.code)
        assertEquals("\"fresh-e\"", result.etag)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        // text/calendar (без charset в сравнении — сервер мог дописать).
        assertEquals("text/calendar", recorded.getHeader("Content-Type")?.substringBefore(';'))
        assertNull(recorded.getHeader("If-Match"))
        assertEquals("*", recorded.getHeader("If-None-Match"))
        assertTrue(recorded.body.readUtf8().contains("BEGIN:VEVENT"))
    }

    @Test
    fun `update with known etag sends if-match and surfaces 412`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(412))
        val url = server.url("/calendars/u%40yandex.ru/events-1/test-uid.ics").toString()

        val result = api.putEvent(url, icsBody(), "user", "pass", etag = "\"stale-e\"", createIfAbsent = false)

        assertEquals(412, result.code)
        val recorded = server.takeRequest()
        assertEquals("\"stale-e\"", recorded.getHeader("If-Match"))
        assertNull(recorded.getHeader("If-None-Match"))
    }

    @Test
    fun `delete returns accepted and not-found codes as-is`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(204, api.deleteEvent(server.url("/x/a.ics").toString(), "user", "pass"))

        // 404 — «объекта и так нет»: движок трактует как успех, код доходит.
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(404, api.deleteEvent(server.url("/x/b.ics").toString(), "user", "pass"))
    }

    @Test
    fun `auth failure on write throws typed exception`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val thrown = runCatching {
            api.putEvent(server.url("/x/a.ics").toString(), icsBody(), "user", "wrong", etag = null, createIfAbsent = true)
        }

        assertTrue(thrown.exceptionOrNull() is CaldavSyncException.Auth)
    }

    @Test
    fun `pull event reads single object with absolute href and etag`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version='1.0' encoding='utf-8'?>
                <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                <D:response><D:href>/calendars/u%40yandex.ru/events-1/test-uid.ics</D:href>
                <D:propstat><D:prop>
                <D:getetag>"tok-1"</D:getetag>
                <C:calendar-data>BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:test-uid
                DTSTAMP:20261109T200000Z
                DTSTART;VALUE=DATE:20261117
                DTEND;VALUE=DATE:20261118
                SUMMARY:Точечная вычитка
                END:VEVENT
                END:VCALENDAR</C:calendar-data>
                </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
                </D:multistatus>
                """.trimIndent(),
            ),
        )
        val url = server.url("/calendars/u%40yandex.ru/events-1/test-uid.ics").toString()

        val remote = api.pullEvent(url, "user", "pass")

        assertEquals("\"tok-1\"", remote?.etag)
        assertEquals("test-uid", remote?.event?.uid)
        assertEquals("Точечная вычитка", remote?.event?.title)
        assertEquals("2026-11-17", remote?.event?.date)
        assertTrue(remote?.href?.endsWith("/test-uid.ics") == true)
    }

    @Test
    fun `pull event on 404 reports absent object`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        assertNull(api.pullEvent(server.url("/x/gone.ics").toString(), "user", "pass"))
    }

    @Test
    fun `derived href encodes uid and appends ics`() {
        assertEquals(
            "https://caldav.yandex.ru/calendars/u%40yandex.ru/events-1/uid-1.ics",
            deriveObjectHref("https://caldav.yandex.ru/calendars/u%40yandex.ru/events-1", "uid-1"),
        )
    }
}
