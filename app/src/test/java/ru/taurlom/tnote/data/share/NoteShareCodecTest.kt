package ru.taurlom.tnote.data.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.domain.model.SharedList

class NoteShareCodecTest {

    @Test
    fun `round-trip — заметка с тегами и фото переживает кодирование`() {
        val decoded = NoteShareCodec.decode(
            NoteShareCodec.encode(
                title = "Рецепт",
                content = "Тесто\n![](note_photos/a.jpg)\nВыпекать",
                tags = listOf("кухня", "выпечка"),
                photos = listOf(
                    NoteShareCodec.SharedNotePhoto("note_photos/a.jpg", byteArrayOf(1, 2, 3)),
                ),
            ),
        )
        assertEquals("Рецепт", decoded.title)
        assertEquals("Тесто\n![](note_photos/a.jpg)\nВыпекать", decoded.content)
        assertEquals(listOf("кухня", "выпечка"), decoded.tags)
        assertEquals(1, decoded.photos.size)
        assertEquals("note_photos/a.jpg", decoded.photos[0].name)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoded.photos[0].data)
    }

    @Test
    fun `чужой json отклоняется`() {
        runCatching { NoteShareCodec.decode("""{"hello":"world"}""") }
            .onSuccess { throw AssertionError("Должен быть IllegalArgumentException") }
    }

    @Test
    fun `файл списка не принимается за заметку`() {
        val listJson = ListShareCodec.encode(
            ru.taurlom.tnote.domain.model.SharedList("Продукты", null, emptyList()),
        )
        runCatching { NoteShareCodec.decode(listJson) }
            .onSuccess { throw AssertionError("Должен быть IllegalArgumentException") }
    }

    @Test
    fun `заметка без заголовка отклоняется`() {
        val raw = NoteShareCodec.encode("   ", "текст", emptyList(), emptyList())
        runCatching { NoteShareCodec.decode(raw) }
            .onSuccess { throw AssertionError("Должен быть IllegalArgumentException") }
    }

    @Test
    fun `длинные поля срезаются до лимитов`() {
        val decoded = NoteShareCodec.decode(
            NoteShareCodec.encode(
                title = "з".repeat(NoteShareCodec.MAX_TITLE_LENGTH + 100),
                content = "т".repeat(NoteShareCodec.MAX_CONTENT_LENGTH + 100),
                tags = (1..NoteShareCodec.MAX_TAGS + 10).map { "тег$it" },
                photos = emptyList(),
            ),
        )
        assertEquals(NoteShareCodec.MAX_TITLE_LENGTH, decoded.title.length)
        assertEquals(NoteShareCodec.MAX_CONTENT_LENGTH, decoded.content.length)
        assertEquals(NoteShareCodec.MAX_TAGS, decoded.tags.size)
    }

    @Test
    fun `слишком много фото — отказ целиком`() {
        val photos = (1..NoteShareCodec.MAX_PHOTOS + 1).map {
            NoteShareCodec.SharedNotePhoto("note_photos/$it.jpg", byteArrayOf(1))
        }
        val raw = NoteShareCodec.encode("Заметка", "", emptyList(), photos)
        runCatching { NoteShareCodec.decode(raw) }
            .onSuccess { throw AssertionError("Должен быть IllegalArgumentException") }
    }

    @Test
    fun `битый base64 фото пропускается, заметка выживает`() {
        // Руками, без encode: он бы не записал битые данные.
        val raw = """
            {
              "tnoteNote": 1,
              "title": "Заметка",
              "content": "![](note_photos/a.jpg)",
              "photos": [{"name": "note_photos/a.jpg", "data": "!!!не-base64!!!"}]
            }
        """.trimIndent()
        val decoded = NoteShareCodec.decode(raw)
        assertEquals("Заметка", decoded.title)
        assertTrue(decoded.photos.isEmpty())
    }

    @Test
    fun `магическое поле первым — sniff по первым байтам работает`() {
        val raw = NoteShareCodec.encode("Заметка", "текст", emptyList(), emptyList())
        assertTrue(raw.take(64).contains("\"${NoteShareCodec.FORMAT_KEY}\""))
    }
}
