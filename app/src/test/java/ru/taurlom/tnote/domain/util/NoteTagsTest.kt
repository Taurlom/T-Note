package ru.taurlom.tnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.domain.model.Note

/**
 * [NoteTags] — нормализация, ввод/вывод и поиск по тегам заметки.
 * Контракты, от которых зависят маппер (колонка notes.tags) и фильтр
 * поиска в списке заметок.
 */
class NoteTagsTest {

    private fun note(vararg tags: String, content: String = "") = Note(
        id = 1,
        title = "Борщ",
        content = content,
        createdAt = 0L,
        tags = tags.toList(),
    )

    // ── Ввод в редакторе ──

    @Test
    fun `input is split by commas, semicolons and whitespace`() {
        assertEquals(
            listOf("рецепт", "кулинария", "на-выходные"),
            NoteTags.fromInput("рецепт, кулинария;на-выходные"),
        )
        assertEquals(listOf("рецепт", "кулинария"), NoteTags.fromInput("рецепт  кулинария"))
        assertEquals(listOf("рецепт"), NoteTags.fromInput("рецепт\n"))
    }

    @Test
    fun `input trims, drops empties and dedupes ignoring case`() {
        // Дубль в другом регистре отбрасывается, написание первого сохраняется.
        assertEquals(
            listOf("Рецепт", "КУЛИНАРИЯ"),
            NoteTags.fromInput(" Рецепт ,  , рецепт , КУЛИНАРИЯ "),
        )
        assertEquals(emptyList<String>(), NoteTags.fromInput("  , ; "))
    }

    @Test
    fun `overlong tags are dropped, count is capped`() {
        val long = "а".repeat(NoteTags.MAX_TAG_LENGTH + 1)
        assertEquals(listOf("рецепт"), NoteTags.fromInput("$long, рецепт"))

        val many = (1..NoteTags.MAX_TAGS + 5).joinToString(",") { "тег$it" }
        assertEquals(NoteTags.MAX_TAGS, NoteTags.fromInput(many).size)
    }

    @Test
    fun `toInput round-trips through fromInput`() {
        val tags = listOf("рецепт", "кулинария")
        assertEquals(tags, NoteTags.fromInput(NoteTags.toInput(tags)))
    }

    // ── Хранение в колонке ──

    @Test
    fun `stored format joins with newline and parses back`() {
        val tags = listOf("рецепт", "кулинария")
        assertEquals("рецепт\nкулинария", NoteTags.toStored(tags))
        assertEquals(tags, NoteTags.parseStored("рецепт\nкулинария"))
    }

    @Test
    fun `empty stored column parses to empty list`() {
        assertEquals(emptyList<String>(), NoteTags.parseStored(""))
    }

    @Test
    fun `stored values are normalized`() {
        assertEquals("рецепт", NoteTags.toStored(listOf(" рецепт ", "рецепт")))
        assertEquals(listOf("рецепт"), NoteTags.parseStored("рецепт\n\n рецепт \n"))
    }

    // ── Поиск ──

    @Test
    fun `hash query matches whole tag ignoring case`() {
        val target = note("рецепт", "кулинария")
        assertTrue(NoteTags.matches(target, "#рецепт"))
        assertTrue(NoteTags.matches(target, "#РЕЦЕПТ"))
        assertTrue(NoteTags.matches(target, "  #рецепт  "))
        assertTrue(NoteTags.matches(note("Рецепт"), "#рецепт"))
    }

    @Test
    fun `hash query does not match partial tag`() {
        val target = note("рецепт")
        assertFalse(NoteTags.matches(target, "#рецепты"))
        assertFalse(NoteTags.matches(target, "#цепт"))
        assertFalse(NoteTags.matches(target, "#кулинария"))
    }

    @Test
    fun `hash query searches tags only, not text`() {
        // #рецепт в тексте (заголовок markdown) — не тег.
        assertFalse(NoteTags.matches(note(content = "# рецепт борща"), "#рецепт"))
    }

    @Test
    fun `lone hash matches anything like empty query`() {
        // Пользователь ещё печатает — фильтр не срабатывает.
        assertTrue(NoteTags.matches(note("рецепт"), "#"))
        assertTrue(NoteTags.matches(note(), "#"))
    }

    @Test
    fun `plain query falls back to substring search`() {
        val target = note("рецепт", content = "Щи со сметаной")
        assertTrue(NoteTags.matches(target, "борщ"))
        assertTrue(NoteTags.matches(target, "сметан"))
        // Обычный поиск теги не подсвечивает: «рецепт» в теге не матчится.
        assertFalse(NoteTags.matches(note(content = "щи"), "рецепт"))
    }

    @Test
    fun `blank query matches anything`() {
        assertTrue(NoteTags.matches(note(), ""))
        assertTrue(NoteTags.matches(note(), "   "))
    }
}
