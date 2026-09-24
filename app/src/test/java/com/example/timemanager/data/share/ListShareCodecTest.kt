package com.example.timemanager.data.share

import com.example.timemanager.domain.model.SharedList
import com.example.timemanager.domain.model.SharedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListShareCodecTest {

    @Test
    fun `round-trip — список переживает кодирование`() {
        val source = SharedList(
            name = "Продукты",
            color = 0xFFB85C38,
            tasks = listOf(
                SharedTask("Молоко", completed = true),
                SharedTask("Хлеб", description = "цельнозерновой"),
                SharedTask("Яблоки")
            )
        )
        val decoded = ListShareCodec.decode(ListShareCodec.encode(source))
        assertEquals(source, decoded)
    }

    @Test
    fun `чужой json отклоняется`() {
        runCatching { ListShareCodec.decode("""{"hello":"world"}""") }
            .onSuccess { throw AssertionError("Должен быть IllegalArgumentException") }
    }

    @Test
    fun `не json отклоняется`() {
        assertTrue(
            runCatching { ListShareCodec.decode("это не json") }.isFailure
        )
    }

    @Test
    fun `цвет без значения читается как null`() {
        val raw = ListShareCodec.encode(
            SharedList(name = "Дела", color = null, tasks = emptyList())
        )
        assertNull(ListShareCodec.decode(raw).color)
    }

    @Test
    fun `пустые названия пунктов пропускаются`() {
        val raw = """
            {
              "tnoteList": 1,
              "name": "Тест",
              "color": 1,
              "tasks": [
                {"title": "  "},
                {"title": "Есть"},
                {}
              ]
            }
        """.trimIndent()
        val decoded = ListShareCodec.decode(raw)
        assertEquals(listOf("Есть"), decoded.tasks.map { it.title })
    }

    @Test
    fun `слишком много пунктов отклоняется`() {
        val tasks = (1..(ListShareCodec.MAX_TASKS + 1)).joinToString(",") {
            """{"title":"пункт $it"}"""
        }
        val raw = """{"tnoteList":1,"name":"Огромный","tasks":[$tasks]}"""
        assertTrue(runCatching { ListShareCodec.decode(raw) }.isFailure)
    }

    @Test
    fun `длинные поля срезаются до лимитов`() {
        val raw = """
            {"tnoteList":1,"name":"${"и".repeat(ListShareCodec.MAX_NAME_LENGTH + 50)}",
             "tasks":[{"title":"${"т".repeat(300)}"}]}
        """.trimIndent()
        val decoded = ListShareCodec.decode(raw)
        assertEquals(ListShareCodec.MAX_NAME_LENGTH, decoded.name.length)
        assertEquals(ListShareCodec.MAX_TITLE_LENGTH, decoded.tasks.single().title.length)
    }

    @Test
    fun `имя файла для отправки безопасное`() {
        assertEquals("Продукты_на_неделю", sanitizeFileName("Продукты/ на: неделю"))
        assertEquals("tnote-list", sanitizeFileName("///"))
        assertEquals("Список_1", sanitizeFileName("Список 1"))
    }
}
