package ru.taurlom.tnote.data.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * (код + ETag) и тип исключения при отказе авторизации. Рядом — читательская
 * часть контракта: точечный PROPFIND различает «объекта нет» и «не прочитался»,
 * а полная вычитка требует 207 multistatus и честно сообщает о неполном
 * снапшоте. Robolectric нужен, потому что оба пути падают в тот же разбор
 * multistatus (DavXml через android.util.Xml).
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
    fun `put body carries utf-8 bom for yandex charset sniffing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val url = server.url("/calendars/u%40yandex.ru/events-1/test-uid.ics").toString()

        api.putEvent(url, icsBody(), "user", "pass", etag = null, createIfAbsent = true)

        val recorded = server.takeRequest()
        val bytes = recorded.body.readByteArray()
        // Бом-префикс: без него парсер Яндекса декодирует тело как cp1252
        // и хранит кракозябры (charset=... он игнорирует, проба 2026-11).
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)
        assertTrue(String(bytes, Charsets.UTF_8).contains("BEGIN:VEVENT"))
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
    fun `conditional delete with etag sends if-match and surfaces 412`() = runBlocking {
        // Tombstone помнит ETag удалённого объекта: DELETE идёт условно,
        // сервер правился после вычитки — 412, конфликт решает движок.
        server.enqueue(MockResponse().setResponseCode(412))
        val url = server.url("/calendars/u%40yandex.ru/events-1/test-uid.ics").toString()

        val code = api.deleteEvent(url, "user", "pass", etag = "\"stale-e\"")

        assertEquals(412, code)
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("\"stale-e\"", recorded.getHeader("If-Match"))
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

        val result = api.pullEvent(url, "user", "pass")
        val remote = (result as CaldavObjectResult.Found).event

        assertEquals("\"tok-1\"", remote.etag)
        assertEquals("test-uid", remote.event.uid)
        assertEquals("Точечная вычитка", remote.event.title)
        assertEquals("2026-11-17", remote.event.date)
        assertTrue(remote.href.endsWith("/test-uid.ics"))
    }

    @Test
    fun `pull event on 404 reports absent object`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(
            CaldavObjectResult.NotFound,
            api.pullEvent(server.url("/x/gone.ics").toString(), "user", "pass"),
        )
    }

    @Test
    fun `pull event on unreadable body reports unreadable, not absence`() = runBlocking {
        // Объект на сервере есть, а прочесть его нечем (битый ICS). Для
        // движка это НЕ «объекта нет»: пересоздание PUT'ом затёрло бы живую
        // серверную правку, поэтому исход отдельный и с причиной.
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry("/x/broken.ics", "\"tok-2\"", "это не VCALENDAR вовсе"),
                ),
            ),
        )

        val result = api.pullEvent(server.url("/x/broken.ics").toString(), "user", "pass")

        assertTrue(result is CaldavObjectResult.Unreadable)
        assertTrue((result as CaldavObjectResult.Unreadable).reason.isNotBlank())
    }

    @Test
    fun `pull event on entry without calendar-data reports unreadable`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """
                <?xml version='1.0' encoding='utf-8'?>
                <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                <D:response><D:href>/x/prop-only.ics</D:href>
                <D:propstat><D:prop><D:getetag>"tok-3"</D:getetag></D:prop>
                <D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
                </D:multistatus>
                """.trimIndent(),
            ),
        )

        val result = api.pullEvent(server.url("/x/prop-only.ics").toString(), "user", "pass")

        assertTrue(result is CaldavObjectResult.Unreadable)
    }

    @Test
    fun `href outside the server origin is refused`() = runBlocking {
        // Ответ REPORT с абсолютным href на чужом хосте: по этому адресу
        // движок отправил бы и следующую вычитку, и PUT/DELETE с паролем,
        // то есть подписка молча «переехала» бы на чужой сервер.
        val collection = server.url("/calendars/u%40yandex.ru/events-1/").toString()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry("https://evil.example/calendars/x/1.ics", "\"e-1\"", validIcs()),
                ),
            ),
        )

        val thrown = runCatching { api.pullEvents(collection, "user", "pass") }

        val error = thrown.exceptionOrNull()
        assertTrue(error is CaldavSyncException.Server)
        assertTrue(error?.message?.contains("вне своего адреса") == true)
    }

    @Test
    fun `full pull on non-207 answer aborts the run`() = runBlocking {
        // Перехваченный запрос: прокси ответил 200 своей страницей. Пустым
        // снапшотом это быть не может — вычитка обязана 207 multistatus.
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>Sign in</body></html>"))

        val thrown = runCatching {
            api.pullEvents(server.url("/calendars/u%40yandex.ru/events-1/").toString(), "user", "pass")
        }

        assertTrue(thrown.exceptionOrNull() is CaldavSyncException.Server)
    }

    @Test
    fun `full pull counts entries it could not read and reports snapshot incomplete`() = runBlocking {
        // Живой вид ответа: корень коллекции (его Depth 1 перечисляет без
        // тела — это не объект, полноте он не мешает), годный VEVENT и
        // объект, который мы разобрать не смогли. Такой снапшот неполон:
        // удаления по нему движок не делает.
        val collection = server.url("/calendars/u%40yandex.ru/events-1/").toString()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry("/calendars/u%40yandex.ru/events-1/", "\"root\"", null),
                    davEntry("/calendars/u%40yandex.ru/events-1/good.ics", "\"e-1\"", validIcs()),
                    davEntry("/calendars/u%40yandex.ru/events-1/bad.ics", "\"e-2\"", "не календарь"),
                    davEntry("/calendars/u%40yandex.ru/events-1/bodyless.ics", "\"e-3\"", null),
                ),
            ),
        )

        val pulled = api.pullEvents(collection, "user", "pass")

        assertEquals(listOf("good-uid"), pulled.events.map { it.event.uid })
        // Битое тело и объект без тела — две непрочитанные записи; корень
        // коллекции (href с косой чертой на конце) среди них не считается.
        assertEquals(2, pulled.unreadable)
        assertFalse(pulled.complete)
    }

    @Test
    fun `full pull of a readable collection is complete`() = runBlocking {
        val collection = server.url("/calendars/u%40yandex.ru/events-1/").toString()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry("/calendars/u%40yandex.ru/events-1/good.ics", "\"e-1\"", validIcs()),
                ),
            ),
        )

        val pulled = api.pullEvents(collection, "user", "pass")

        assertEquals(0, pulled.unreadable)
        assertTrue(pulled.complete)
    }

    /**
     * Серия на сервере — один ресурс с мастером и исключениями. Строкой живёт
     * мастер (иначе компоненты по очереди перезаписывали бы одну запись), а
     * сам объект помечается «не перезаписывать».
     */
    @Test
    fun `pull keeps the series master and marks the resource unwritable`() = runBlocking {
        val collection = server.url("/calendars/u%40yandex.ru/events-1/").toString()
        val seriesIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:series@example.com
            DTSTAMP:20261109T200000Z
            DTSTART;VALUE=DATE:20261117
            DTEND;VALUE=DATE:20261118
            SUMMARY:Зарядка
            RRULE:FREQ=DAILY;COUNT=10
            END:VEVENT
            BEGIN:VEVENT
            UID:series@example.com
            RECURRENCE-ID;VALUE=DATE:20261119
            DTSTAMP:20261109T200001Z
            DTSTART;VALUE=DATE:20261121
            DTEND;VALUE=DATE:20261122
            SUMMARY:Зарядка
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry("/calendars/u%40yandex.ru/events-1/series.ics", "\"e-1\"", seriesIcs),
                ),
            ),
        )

        val pulled = api.pullEvents(collection, "user", "pass")

        // Один UID — одна запись, и это мастер: якорь 17-го, а не 21-го.
        val remote = pulled.events.single()
        assertEquals("series@example.com", remote.event.uid)
        assertEquals("2026-11-17", remote.event.date)
        // Исключения рядом с мастером — данные, которые PUT одним VEVENT стёр бы.
        assertTrue(remote.notReproducible)
        assertEquals(0, pulled.unreadable)
        assertTrue(pulled.complete)
    }

    /** Ресурс из одних исключений — непрочитанный: «объекта нет» выдавать нельзя. */
    @Test
    fun `resource without a master is unreadable, not deleted`() = runBlocking {
        val collection = server.url("/calendars/u%40yandex.ru/events-1/").toString()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry(
                        "/calendars/u%40yandex.ru/events-1/orphan.ics",
                        "\"e-1\"",
                        """
                            BEGIN:VCALENDAR
                            VERSION:2.0
                            BEGIN:VEVENT
                            UID:orphan@example.com
                            RECURRENCE-ID;VALUE=DATE:20261119
                            DTSTAMP:20261109T200001Z
                            DTSTART;VALUE=DATE:20261121
                            DTEND;VALUE=DATE:20261122
                            SUMMARY:Вхождение без мастера
                            END:VEVENT
                            END:VCALENDAR
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val pulled = api.pullEvents(collection, "user", "pass")

        assertTrue(pulled.events.isEmpty())
        assertEquals(1, pulled.unreadable)
        assertFalse(pulled.complete)
    }

    /** Точечная перечитка конфликтного пути — тот же алгоритм: мастер, не firstOrNull. */
    @Test
    fun `point read returns the master of the resource`() = runBlocking {
        val url = server.url("/calendars/u%40yandex.ru/events-1/series.ics").toString()
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                multistatusOf(
                    davEntry(
                        "/calendars/u%40yandex.ru/events-1/series.ics",
                        "\"e-2\"",
                        """
                            BEGIN:VCALENDAR
                            VERSION:2.0
                            BEGIN:VEVENT
                            UID:series@example.com
                            RECURRENCE-ID;VALUE=DATE:20261119
                            DTSTAMP:20261109T200001Z
                            DTSTART;VALUE=DATE:20261121
                            DTEND;VALUE=DATE:20261122
                            SUMMARY:Зарядка
                            END:VEVENT
                            BEGIN:VEVENT
                            UID:series@example.com
                            DTSTAMP:20261109T200000Z
                            DTSTART;VALUE=DATE:20261117
                            DTEND;VALUE=DATE:20261118
                            SUMMARY:Зарядка
                            RRULE:FREQ=DAILY;COUNT=10
                            END:VEVENT
                            END:VCALENDAR
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val found = api.pullEvent(url, "user", "pass")

        // Исключение стоит первым — мастер всё равно берётся мастер.
        val remote = (found as CaldavObjectResult.Found).event
        assertEquals("2026-11-17", remote.event.date)
        assertTrue(remote.notReproducible)
    }

    @Test
    fun `derived href encodes uid and appends ics`() {
        assertEquals(
            "https://caldav.yandex.ru/calendars/u%40yandex.ru/events-1/uid-1.ics",
            deriveObjectHref("https://caldav.yandex.ru/calendars/u%40yandex.ru/events-1", "uid-1"),
        )
    }

    @Test
    fun `etag without quotes is quoted in response and if-match`() = runBlocking {
        // Форма Яндекса: ETag приходит и уходит без кавычек. If-Match он
        // признаёт только в цитированной форме — нормализация должна быть
        // на границе транспорта, а не в голове движка.
        server.enqueue(MockResponse().setResponseCode(201).addHeader("ETag", "1794258807976"))

        val result = api.putEvent(
            server.url("/x/a.ics").toString(),
            icsBody(),
            "user",
            "pass",
            etag = "1794258807976",
            createIfAbsent = false,
        )

        assertEquals("\"1794258807976\"", result.etag)
        val recorded = server.takeRequest()
        assertEquals("\"1794258807976\"", recorded.getHeader("If-Match"))
    }

    /** Ответ PROPFIND/REPORT: обёртка DAV:multistatus над готовыми записями. */
    private fun multistatusOf(vararg entries: String) = buildString {
        append("<?xml version='1.0' encoding='utf-8'?>")
        append("<D:multistatus xmlns:D=\"DAV:\" xmlns:C=\"urn:ietf:params:xml:ns:caldav\">")
        entries.forEach { append(it) }
        append("</D:multistatus>")
    }

    /** Одна запись multistatus: href + ETag + calendar-data (null — тела нет). */
    private fun davEntry(href: String, etag: String, calendarData: String?): String = buildString {
        append("<D:response><D:href>").append(href).append("</D:href>")
        append("<D:propstat><D:prop><D:getetag>").append(etag).append("</D:getetag>")
        if (calendarData != null) {
            append("<C:calendar-data>").append(calendarData).append("</C:calendar-data>")
        }
        append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>")
    }

    private fun validIcs(): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:good-uid
        DTSTAMP:20261109T200000Z
        DTSTART;VALUE=DATE:20261117
        DTEND;VALUE=DATE:20261118
        SUMMARY:Годный объект
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
