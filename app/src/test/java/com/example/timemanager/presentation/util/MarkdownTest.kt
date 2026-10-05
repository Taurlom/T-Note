package com.example.timemanager.presentation.util

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTextApi::class)
class MarkdownTest {

    @Test
    fun `heading strips marker and bolds line`() {
        val rendered = Markdown.render("# Рецепт борща")
        assertEquals("Рецепт борща", rendered.text)
        val bold = rendered.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0, bold.start)
        assertEquals(rendered.text.length, bold.end)
    }

    @Test
    fun `inline bold keeps neighbours`() {
        val rendered = Markdown.render("Тесто **дрожжевое**, соль")
        assertEquals("Тесто дрожжевое, соль", rendered.text)
        val bold = rendered.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals("дрожжевое", rendered.text.substring(bold.start, bold.end))
    }

    @Test
    fun `unclosed bold stays literal`() {
        val rendered = Markdown.render("было **не закрыто")
        assertEquals("было **не закрыто", rendered.text)
    }

    @Test
    fun `link becomes url annotation`() {
        val rendered = Markdown.render("Смотри [тут](https://example.com) рецепт")
        assertEquals("Смотри тут рецепт", rendered.text)
        val link = rendered.getLinkAnnotations(0, rendered.text.length)
            .mapNotNull { it.item as? LinkAnnotation.Url }
            .first()
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun `link scheme case does not matter`() {
        val rendered = Markdown.render("[тут](HTTP://example.com)")
        assertEquals("HTTP://example.com", rendered.getLinkAnnotations(0, rendered.text.length)
            .mapNotNull { it.item as? LinkAnnotation.Url }
            .first().url)
    }

    @Test
    fun `mailto and tel become links`() {
        listOf("mailto:mail@example.com", "tel:+79990000000").forEach { url ->
            val rendered = Markdown.render("[связь]($url)")
            assertEquals(url, rendered.getLinkAnnotations(0, rendered.text.length)
                .mapNotNull { it.item as? LinkAnnotation.Url }
                .first().url)
        }
    }

    @Test
    fun `non-whitelisted or schemeless link stays literal`() {
        listOf(
            "javascript:alert(1)",
            "content://x",
            "file:///etc/hosts",
            "intent://x",
            "qwerty://x",
            "example.com"
        ).forEach { url ->
            val source = "[текст]($url)"
            val rendered = Markdown.render(source)
            // Ни ссылки, ни подчёркивания: текст — исходный синтаксис целиком.
            assertTrue(rendered.getLinkAnnotations(0, rendered.text.length).isEmpty())
            assertEquals(source, rendered.text)
        }
    }

    @Test
    fun `bullet marker becomes dot`() {
        val rendered = Markdown.render("- мука\n- молоко")
        assertEquals("• мука\n• молоко", rendered.text)
    }

    @Test
    fun `numbered list and plain lines pass through`() {
        val rendered = Markdown.render("1. замесить\n2. выпекать")
        assertEquals("1. замесить\n2. выпекать", rendered.text)
    }

    @Test
    fun `preview strips all markers`() {
        val preview = Markdown.preview(
            "# Борщ\nСвиной **мясо**\n- свёкла\n[рецепт](https://x)",
            maxLength = 120
        )
        assertEquals("Борщ Свиной мясо свёкла рецепт", preview)
    }

    // ---------- редактирование ----------

    @Test
    fun `toggleBold wraps selection`() {
        val (text, sel) = MarkdownEditing.toggleBold("тесто", TextRange(0, 5))
        assertEquals("**тесто**", text)
        assertEquals(TextRange(2, 7), sel)
    }

    @Test
    fun `toggleBold unwraps already bold`() {
        val (text, sel) = MarkdownEditing.toggleBold("**тесто**", TextRange(2, 7))
        assertEquals("тесто", text)
        assertEquals(TextRange(0, 5), sel)
    }

    @Test
    fun `toggleLinePrefix adds and removes on current line`() {
        val (added, caret) = MarkdownEditing.toggleLinePrefix(
            "шаг1\nшаг2", TextRange(8), prefix = "# "
        )
        assertEquals("шаг1\n# шаг2", added)
        assertEquals(10, caret.end)
        val (removed, _) = MarkdownEditing.toggleLinePrefix(added, TextRange(10), prefix = "# ")
        assertEquals("шаг1\nшаг2", removed)
    }

    @Test
    fun `toggleLinePrefix at very beginning of text`() {
        val (added, _) = MarkdownEditing.toggleLinePrefix("текст", TextRange(2), prefix = "- ")
        assertEquals("- текст", added)
    }

    @Test
    fun `insertLink wraps selection and parks caret in url`() {
        val (text, sel) = MarkdownEditing.insertLink("смотри тут", TextRange(7, 10), "текст")
        assertEquals("смотри [тут]()", text)
        assertEquals(13, sel.start)
    }

    @Test
    fun `insertLink without selection uses placeholder label`() {
        val (text, _) = MarkdownEditing.insertLink("", TextRange(0), "текст")
        assertEquals("[текст]()", text)
    }
}
