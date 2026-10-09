package ru.taurlom.tnote.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    // ---------- preview ----------

    @Test
    fun `preview strips all markers`() {
        val preview = Markdown.preview(
            "# Борщ\nСвиной **мясо**\n- свёкла\n[рецепт](https://x)",
            maxLength = 120,
        )
        assertEquals("Борщ Свиной мясо свёкла рецепт", preview)
    }

    @Test
    fun `preview drops image refs`() {
        val preview = Markdown.preview("Тесто\n![](note_photos/1.jpg)\nвыпечка")
        assertEquals("Тесто выпечка", preview)
    }

    @Test
    fun `preview collapses whitespace after stripping`() {
        val preview = Markdown.preview("мука  \n\n![](note_photos/2.png)  \nсоль")
        assertEquals("мука соль", preview)
    }

    @Test
    fun `preview truncates by word boundary`() {
        val preview = Markdown.preview("один два три четыре пять", maxLength = 10)
        assertEquals("один два…", preview)
    }

    // ---------- isOpenableLink ----------

    @Test
    fun `whitelisted schemes are openable`() {
        listOf(
            "https://example.com",
            "http://example.com",
            "mailto:mail@example.com",
            "tel:+79990000000",
            "HTTP://example.com",
        ).forEach { url ->
            assertTrue(url, Markdown.isOpenableLink(url))
        }
    }

    @Test
    fun `non-whitelisted or schemeless links are not openable`() {
        listOf(
            "javascript:alert(1)",
            "content://x",
            "file:///etc/hosts",
            "intent://x",
            "qwerty://x",
            "example.com",
        ).forEach { url ->
            assertFalse(url, Markdown.isOpenableLink(url))
        }
    }
}
