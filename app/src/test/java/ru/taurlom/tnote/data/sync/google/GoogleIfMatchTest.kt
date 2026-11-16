package ru.taurlom.tnote.data.sync.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Форма предусловия условной правки Google: If-Match по RFC 7232 — только
 * в кавычках (значение «etag» из тела Google приходит уже цитированным,
 * normalizeEtag их не удваивает); пустое значение и слабый валидатор «W/…»
 * предусловия не дают — PUT уходит безусловно (см. GoogleCalendarApi).
 */
class GoogleIfMatchTest {

    @Test
    fun `quoted etag passes through unchanged`() {
        assertEquals("\"3234758983260000\"", googleIfMatch("\"3234758983260000\""))
    }

    @Test
    fun `unquoted etag is quoted`() {
        assertEquals("\"3234758983260000\"", googleIfMatch("3234758983260000"))
    }

    @Test
    fun `absent etag gives no precondition`() {
        assertNull(googleIfMatch(null))
        assertNull(googleIfMatch(""))
        assertNull(googleIfMatch("   "))
    }

    @Test
    fun `weak validator never goes into if-match`() {
        // Слабый ETag в If-Match не совпадёт никогда (RFC 9110, strong
        // comparison) — предусловие не выставляем вовсе.
        assertNull(googleIfMatch("W/\"abc\""))
    }
}
