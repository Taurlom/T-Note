package ru.taurlom.tnote.presentation.screens.notes

import com.mohamedrejeb.richeditor.model.RichTextState
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.domain.util.NoteImageRefs

/**
 * Конвейер изображений заметки на compose-rich-editor 1.2.1: вставка
 * `<img>` в редактор, сериализация в markdown и обратный парсинг в
 * инлайн-контент. Плейсхолдер картинки в тексте — U+FFFD (дефолтный
 * alternateText у `appendInlineContent`).
 */
class NoteImagePipelineTest {

    @Test
    fun `setMarkdown превращает image-разметку в инлайн-заполнитель`() {
        val state = RichTextState()
        state.setMarkdown("Привет\n\n![](note_photos/abc.jpg)")

        assertTrue(
            "в тексте ожидался символ-заполнитель U+FFFD, текст: ${state.annotatedString.text.toList()}",
            state.annotatedString.text.contains('\uFFFD'),
        )
    }

    @Test
    fun `setMarkdown + toMarkdown сохраняет относительный путь картинки`() {
        val state = RichTextState()
        state.setMarkdown("Привет\n\n![](note_photos/abc.jpg)")

        val markdown = state.toMarkdown()
        assertTrue(
            "ожидался ![](note_photos/abc.jpg), получено: $markdown",
            markdown.contains("![](note_photos/abc.jpg)"),
        )
    }

    @Test
    fun `insertHtmlAfterSelection img сериализуется в markdown-картинку`() {
        val state = RichTextState()
        state.insertHtmlAfterSelection("<p><img src=\"content://media/1\"></p>")

        val markdown = state.toMarkdown()
        assertTrue(
            "ожидался ![](content://media/1), получено: $markdown",
            markdown.contains("![](content://media/1)"),
        )
    }

    @Test
    fun `полный цикл сохранения - картинка переживает toMarkdown + setMarkdown`() {
        val editor = RichTextState()
        editor.insertHtmlAfterSelection("<p>Текст</p>")
        editor.insertHtmlAfterSelection("<p><img src=\"note_photos/abc.jpg\"></p>")

        val saved = editor.toMarkdown()
        assertTrue("после сохранения картинка потеряна: $saved", saved.contains("![](note_photos/abc.jpg)"))

        val viewer = RichTextState()
        viewer.setMarkdown(saved)
        assertTrue(
            "после повторного парсинга заполнитель потерян: ${viewer.annotatedString.text.toList()}",
            viewer.annotatedString.text.contains('\uFFFD'),
        )
    }

    @Test
    fun `вставка фото якорями дает картинке отдельный абзац`() {
        val state = RichTextState()
        state.insertHtmlAfterSelection("<p>Текст</p>")
        // Формат из NoteDetailScreen: пустые <p> по краям заставляют
        // insertParagraphs разбивать абзац, а не вклеивать <img> в текущий.
        state.insertHtmlAfterSelection("<p></p><p><img src=\"content://media/1\"></p><p></p>")

        val markdown = state.toMarkdown()
        assertTrue(
            "картинка не на своей строке (ожидается \\n перед ссылкой): $markdown",
            markdown.contains("\n![](content://media/1)"),
        )
        assertTrue(
            "картинка склеена с текстом абзаца: $markdown",
            !markdown.contains("Текст![](content://media/1)"),
        )
    }

    @Test
    fun `вставка нескольких фото якорями дает каждой свой абзац`() {
        val state = RichTextState()
        state.insertHtmlAfterSelection("<p>Текст</p>")
        state.insertHtmlAfterSelection(
            "<p></p><p><img src=\"content://media/1\"></p><p><img src=\"content://media/2\"></p><p></p>",
        )

        val markdown = state.toMarkdown()
        assertTrue(
            "картинки склеились между собой: $markdown",
            !markdown.contains("![](content://media/1)![](content://media/2)"),
        )
        assertTrue(
            "первая картинка не на своей строке: $markdown",
            markdown.contains("\n![](content://media/1)"),
        )
    }

    @Test
    fun `isolateImageRefs разрезает склеенные с текстом ссылки`() {
        val isolated = NoteImageRefs.isolateImageRefs("Текст![](a.jpg)хвост![](b.jpg)")
        assertTrue(
            "ожидалась изоляция обеих ссылок, получено: $isolated",
            isolated == "Текст\n![](a.jpg)\nхвост\n![](b.jpg)",
        )
    }

    @Test
    fun `isolateImageRefs не трогает уже изолированные ссылки`() {
        val source = "Текст\n![](a.jpg)\n![](b.jpg)"
        assertTrue(
            "ожидалось отсутствие изменений, получено: ${NoteImageRefs.isolateImageRefs(source)}",
            NoteImageRefs.isolateImageRefs(source) == source,
        )
    }

    @Test
    fun `isolateImageRefs ставит ссылку в начало и конец текста на свои строки`() {
        val isolated = NoteImageRefs.isolateImageRefs("![](a.jpg)Текст![](b.jpg)")
        assertTrue(
            "ожидалась изоляция с обеих сторон, получено: $isolated",
            isolated == "![](a.jpg)\nТекст\n![](b.jpg)",
        )
    }

    @Test
    fun `загрузка через токены сохраняет текст после подряд идущих картинок`() {
        val state = RichTextState()
        val markdown = "AA **bb** [cc](https://a.b)\n![](note_photos/a.jpg)\n![](note_photos/b.jpg)\nDD"

        setMarkdownWithImages(state, markdown)

        val flat = state.annotatedString.text
        assertTrue("текст после картинок потерян: $flat", flat.contains("DD"))
        assertTrue(
            "ожидалось ровно 2 заглушки, получено: $flat",
            flat.count { it == '\uFFFD' } == 2,
        )
        assertTrue("токен остался в тексте: $flat", !flat.contains('\uE000'))
        val md = state.toMarkdown()
        assertTrue("картинка a потеряна: $md", md.contains("![](note_photos/a.jpg)"))
        assertTrue("картинка b потеряна: $md", md.contains("![](note_photos/b.jpg)"))
        assertTrue(
            "форматирование потеряно: $md",
            md.contains("**bb**") && md.contains("[cc](https://a.b)"),
        )
    }

    @Test
    fun `загрузка через токены сохраняет картинку и соседний текст`() {
        val state = RichTextState()
        setMarkdownWithImages(state, "Первый\n![](note_photos/abc.jpg)\nПоследний")

        val flat = state.annotatedString.text
        assertTrue("картинка потеряна: $flat", flat.contains('\uFFFD'))
        assertTrue(
            "текст потерян: $flat",
            flat.contains("Первый") && flat.contains("Последний"),
        )
        assertTrue(
            "картинка не сохранилась в markdown: ${state.toMarkdown()}",
            state.toMarkdown().contains("![](note_photos/abc.jpg)"),
        )
    }

    @Test
    fun `загрузка без картинок идет напрямую в markdown-парсер`() {
        val state = RichTextState()
        setMarkdownWithImages(state, "Просто текст")
        assertTrue(state.annotatedString.text == "Просто текст")
    }
}
