package ru.taurlom.tnote.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разбор origin — граница, к которой привязан секрет и за которую он не
 * должен уходить: форма «scheme://host[:порт]», нестандартный порт сохраняется
 * (это другой сервер), а всё, что не абсолютный http(s)-URL, остаётся null —
 * адреса Google («calendarId/eventId») проверяются не на origin, а кодом
 * транспорта с фиксированным корнем API.
 */
class ServerOriginTest {

    @Test
    fun `origin drops path and query but keeps non-default port`() {
        assertEquals(
            "https://caldav.yandex.ru",
            originOf("https://caldav.yandex.ru/calendars/user/events-1/"),
        )
        assertEquals("https://host.example:8443", originOf("https://host.example:8443/dav/"))
        // Стандартный порт — тот же origin, что и без него.
        assertEquals("https://host.example", originOf("https://host.example:443/dav/"))
    }

    @Test
    fun `scheme is part of the origin`() {
        // Тот же хост по http — другой origin: пароль туда отдавать нельзя.
        assertEquals("http://caldav.example", originOf("http://caldav.example/dav/"))
        assertNull(httpsOriginOf("http://caldav.example/dav/"))
        assertEquals("https://caldav.example", httpsOriginOf("https://caldav.example/dav/"))
    }

    @Test
    fun `non-url and foreign schemes are not origins`() {
        // Форма ссылки объекта у Google: не URL, проверке origin не подлежит.
        assertNull(originOf("user@gmail.com/abcdef-event-id"))
        assertNull(originOf(""))
        assertNull(originOf("file:///sdcard/x"))
        assertNull(httpsOriginOf("ftp://host.example/dav"))
    }
}
