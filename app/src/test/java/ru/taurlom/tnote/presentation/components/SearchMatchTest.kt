package ru.taurlom.tnote.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [searchMatch] — ядро фильтрации поиска в заметках, документах и списках.
 * Поведение: регистронезависимая подстрока в любом из полей, пустой запрос
 * матчит всё (фильтр не ветвится снаружи).
 */
class SearchMatchTest {

    @Test
    fun `blank query matches anything`() {
        assertTrue(searchMatch("", "заметка"))
        assertTrue(searchMatch("   ", "заметка"))
        assertTrue(searchMatch("", null))
    }

    @Test
    fun `substring match is case-insensitive`() {
        assertTrue(searchMatch("РЕЦЕПТ", "Мой рецепт борща"))
        assertTrue(searchMatch("рецепт", "РЕЦЕПТ борща"))
    }

    @Test
    fun `query is trimmed before matching`() {
        assertTrue(searchMatch("  борщ  ", "рецепт борща"))
    }

    @Test
    fun `matches any of multiple fields`() {
        assertTrue(searchMatch("список покупок", null, "список покупок на неделю"))
        // Подстрока без учёта словоформ: «неделю» находится, «неделя» — нет.
        assertTrue(searchMatch("неделю", "заголовок", "список покупок на неделю"))
        assertFalse(searchMatch("неделя", "заголовок", "список покупок на неделю"))
    }

    @Test
    fun `no match returns false`() {
        assertFalse(searchMatch("пицца", "рецепт борща", "на неделю"))
        assertFalse(searchMatch("борщ", null, null))
    }
}
