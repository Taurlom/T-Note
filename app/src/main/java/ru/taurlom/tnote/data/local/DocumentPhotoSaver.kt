package ru.taurlom.tnote.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Копирование выбранных фото документов в filesDir/document_photos.
 *
 * Публичные методы — suspend и на [Dispatchers.IO]: копирование нескольких
 * тяжёлых JPEG блокировало главный поток (подтормаживания и ANR при
 * сохранении документа с фото).
 */
@Singleton
class DocumentPhotoSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun savePhotos(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        uris.mapNotNull { savePhotoBlocking(it) }
    }

    suspend fun deletePhotos(relativePaths: List<String?>) = withContext(Dispatchers.IO) {
        relativePaths.filterNotNull().forEach {
            File(context.filesDir, it).delete()
        }
    }

    private fun savePhotoBlocking(sourceUri: Uri?): String? {
        if (sourceUri == null) return null

        val photosDir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val fileName = "document_${UUID.randomUUID()}.jpg"
        val destFile = File(photosDir, fileName)

        // openInputStream возвращает null, когда URI «умер» (разрешение истекло
        // после возвращения из камеры) — лучше потерять одно фото, чем оставить
        // в базе ссылку на несуществующий файл. Прерванную копию убираем,
        // чтобы не плодить сирот на диске.
        return try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
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
