package ru.taurlom.tnote.data.local

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.taurlom.tnote.domain.repository.PhotoStorage
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Копирование выбранных фото заметки в filesDir/note_photos.
 *
 * Отдельно от [DocumentPhotoSaver]: разные каталоги означают разную семантику
 * вложений (у документа фото — главное, у заметки — приложение), и удаление
 * раздела не должно задевать чужие файлы. Публичные методы — suspend на
 * [Dispatchers.IO] (см. комментарий в [DocumentPhotoSaver]).
 */
@Singleton
class NotePhotoSaver @Inject constructor(@ApplicationContext private val context: Context) : PhotoStorage {

    override suspend fun savePhotos(contentUris: List<String>): List<String> = withContext(Dispatchers.IO) {
        contentUris.mapNotNull { savePhotoBlocking(it) }
    }

    override suspend fun deletePhotos(relativePaths: List<String>) = withContext(Dispatchers.IO) {
        relativePaths.forEach { File(context.filesDir, it).delete() }
    }

    private fun savePhotoBlocking(sourceUri: String): String? {
        val photosDir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val fileName = "note_${UUID.randomUUID()}.jpg"
        val destFile = File(photosDir, fileName)

        return try {
            val input = context.contentResolver.openInputStream(sourceUri.toUri()) ?: return null
            input.use { src -> destFile.outputStream().use { dst -> src.copyTo(dst) } }
            "$PHOTOS_DIR/$fileName"
        } catch (e: IOException) {
            destFile.delete()
            null
        }
    }

    companion object {
        const val PHOTOS_DIR = "note_photos"
    }
}
