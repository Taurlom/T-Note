package com.example.timemanager.presentation.util

import com.example.timemanager.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareListTest {

    private fun task(
        title: String,
        description: String = "",
        completed: Boolean = false
    ) = Task(id = 0, title = title, description = description, isCompleted = completed, categoryId = 1)

    @Test
    fun `форматирует пункты с чекбоксами и описаниями`() {
        val text = buildListShareText(
            categoryName = "Продукты",
            tasks = listOf(
                task("Молоко", completed = true),
                task("Хлеб", description = "цельнозерновой"),
                task("Яблоки")
            )
        )
        assertEquals(
            """
            Продукты

            ✅ Молоко
            ⬜ Хлеб — цельнозерновой
            ⬜ Яблоки
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `пустой список — только название`() {
        assertEquals("Список", buildListShareText("Список", emptyList()))
    }

    @Test
    fun `обрезает пробелы по краям`() {
        val text = buildListShareText(
            categoryName = "  Дела  ",
            tasks = listOf(task("  Позвонить  ", description = "  в клинику  "))
        )
        assertEquals("Дела\n\n⬜ Позвонить — в клинику", text)
    }
}
