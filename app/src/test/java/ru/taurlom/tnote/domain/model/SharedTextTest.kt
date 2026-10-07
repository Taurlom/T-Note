package ru.taurlom.tnote.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedTextTest {

    @Test
    fun `subject wins over first line`() {
        // Браузер: subject — название страницы, text — ссылка.
        val shared = SharedText(subject = "Как варить кофе", text = "https://example.com/coffee")
        assertEquals("Как варить кофе", shared.draftTitle())
    }

    @Test
    fun `blank subject falls back to first non-blank line`() {
        val shared = SharedText(subject = "   ", text = "\n\nПервая строка\nВторая строка")
        assertEquals("Первая строка", shared.draftTitle())
    }

    @Test
    fun `long first line is capped to title limit`() {
        // Одна строка без переводов (ссылка с параметрами, base64):
        // заголовок не должен тащить её целиком.
        val shared = SharedText(subject = null, text = "a".repeat(300))
        assertEquals(80, shared.draftTitle().length)
    }

    @Test
    fun `long subject is capped to title limit`() {
        val shared = SharedText(subject = "s".repeat(300), text = "текст")
        assertEquals(80, shared.draftTitle().length)
    }

    @Test
    fun `draft title of blank text is empty`() {
        // Вход такие шэры не пропускает, но функция не должна полагаться
        // на дисциплину вызывающего.
        assertEquals("", SharedText(null, " \n \n").draftTitle())
    }

    @Test
    fun `preview collapses blank lines into spaces`() {
        val shared = SharedText(null, "строка один\n\n\n   \nстрока два")
        assertEquals("строка один строка два", shared.preview())
    }

    @Test
    fun `preview keeps short text as is`() {
        val shared = SharedText(null, "короткий текст")
        assertEquals("короткий текст", shared.preview())
    }

    @Test
    fun `preview cuts long text with ellipsis`() {
        val shared = SharedText(null, "x".repeat(500))
        val preview = shared.preview()
        assertEquals(121, preview.length) // 120 символов + «…»
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `preview trimEnd does not eat content`() {
        // Обрезка может попасть на пробел после склейки строк: концевые
        // пробелы перед «…» появляться не должны.
        val shared = SharedText(null, "aaaa\nbbbb\ncccc")
        val preview = shared.preview(10)
        assertEquals("aaaa bbbb…", preview)
    }
}
