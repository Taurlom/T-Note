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
 * Копирование выбранных фото документов в filesDir/document_photos.
 *
 * Публичные методы — suspend и на [Dispatchers.IO]: копирование нескольких
 * тяжёлых JPEG блокировало главный поток (подтормаживания и ANR при
 * сохранении документа с фото).
 *
 * Content-URI принимаются строками: domain-слой (интерфейс [PhotoStorage])
 * не зависит от android.net.Uri.
 */
@Singleton
class DocumentPhotoSaver @Inject constructor(@ApplicationContext private val context: Context) : PhotoStorage {

    override suspend fun savePhotos(contentUris: List<String>): List<String> = withContext(Dispatchers.IO) {
        contentUris.mapNotNull { savePhotoBlocking(it) }
    }

    override suspend fun deletePhotos(relativePaths: List<String>) = withContext(Dispatchers.IO) {
        relativePaths.forEach {
            File(context.filesDir, it).delete()
        }
    }

    private fun savePhotoBlocking(sourceUri: String): String? {
        val photosDir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val fileName = "document_${UUID.randomUUID()}.jpg"
        val destFile = File(photosDir, fileName)

        // openInputStream возвращает null, когда URI «умер» (разрешение истекло
        // после возвращения из камеры) — лучше потерять одно фото, чем оставить
        // в базе ссылку на несуществующий файл. Прерванную копию убираем,
        // чтобы не плодить сирот на диске.
        return try {
            val input = context.contentResolver.openInputStream(sourceUri.toUri()) ?: return null
            input.use { src -> destFile.outputStream().use { dst -> src.copyTo(dst) } }
            "${PHOTOS_DIR}/$fileName"
        } catch (e: IOException) {
            destFile.delete()
            null
        }
    }

    companion object {
        private const val PHOTOS_DIR = "document_photos"
    }
}
