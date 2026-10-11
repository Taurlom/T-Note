package ru.taurlom.tnote.data.share

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.model.SharedList
import java.io.File

/**
 * Круговорот «экспорт → импорт» заметки на JVM (Robolectric): реальный
 * контекст с filesDir/cacheDir/ContentResolver, реальные файлы фото.
 * Проверяем, что байты фото доезжают без искажений, ссылки в тексте
 * переписываются на временные файлы, а путь за пределами filesDir
 * в файл не утекает. Экспорт тестируется через writeShareFile:
 * FileProvider-обёртка под Robolectric на Windows не воспроизводится.
 */
@RunWith(RobolectricTestRunner::class)
class NoteShareRepositoryImplTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = NoteShareRepositoryImpl(context)

    @Test
    fun `экспорт-импорт — текст, теги и фото переживают передачу`() = runBlocking {
        val photo = File(context.filesDir, "note_photos/p1.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(10, 20, 30))
        }
        val note = Note(
            title = "Рецепт",
            content = "Тесто\n![тесто](note_photos/p1.jpg)\nВыпекать",
            createdAt = 1L,
            tags = listOf("кухня"),
        )

        val exported = repository.writeShareFile(note)
        val shared = repository.importFromFile(Uri.fromFile(exported))

        assertEquals("Рецепт", shared.title)
        assertEquals(listOf("кухня"), shared.tags)
        assertEquals(1, shared.photoUris.size)
        // Ссылка в тексте переписана на временный файл из cacheDir.
        val tempUri = shared.photoUris.single()
        assertTrue(shared.content.contains("![тесто]($tempUri)"))
        val tempFile = File(Uri.parse(tempUri).path!!)
        assertTrue(tempFile.exists())
        assertArrayEquals(photo.readBytes(), tempFile.readBytes())
    }

    @Test
    fun `путь за пределами filesDir в экспорт не попадает`() = runBlocking {
        // База после восстановления из чужого бэкапа может содержать
        // что угодно: «../…» читается только внутри filesDir.
        val note = Note(
            title = "Заметка",
            content = "![x](../databases/time_manager.db)",
            createdAt = 1L,
        )
        val exported = repository.writeShareFile(note)
        val shared = repository.importFromFile(Uri.fromFile(exported))
        assertTrue(shared.photoUris.isEmpty())
    }

    @Test
    fun `isNoteFile отличает заметку от списка и мусора`() = runBlocking {
        val noteFile = repository.writeShareFile(Note(title = "N", content = "c", createdAt = 1L))
        val listFile = File(context.cacheDir, "list.tnote").apply {
            writeText(ListShareCodec.encode(SharedList("L", null, emptyList())))
        }
        val garbage = File(context.cacheDir, "garbage.tnote").apply { writeText("not json") }

        assertTrue(repository.isNoteFile(Uri.fromFile(noteFile)))
        assertFalse(repository.isNoteFile(Uri.fromFile(listFile)))
        assertFalse(repository.isNoteFile(Uri.fromFile(garbage)))
    }

    @Test
    fun `чужой файл отклоняется`() = runBlocking {
        val garbage = File(context.cacheDir, "garbage2.tnote").apply { writeText("{\"x\":1}") }
        runCatching { repository.importFromFile(Uri.fromFile(garbage)) }
            .onSuccess { throw AssertionError("Должно быть исключение") }
        Unit
    }
}
