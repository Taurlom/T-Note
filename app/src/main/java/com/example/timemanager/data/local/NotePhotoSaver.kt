package com.example.timemanager.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Копирование выбранных фото заметки в filesDir/[PHOTOS_DIR].
 *
 * Отдельно от [DocumentPhotoSaver]: разные каталоги означают разную семантику
 * вложений (у документа фото — главное, у заметки — приложение), и удаление
 * раздела не должно задевать чужие файлы. Поведение то же: null при
 * недоступном источнике, очистка прерванной копии.
 */
@Singleton
class NotePhotoSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun savePhoto(sourceUri: Uri?): String? {
        if (sourceUri == null) return null

        val photosDir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val fileName = "note_${UUID.randomUUID()}.jpg"
        val destFile = File(photosDir, fileName)

        return try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            input.use { src -> destFile.outputStream().use { dst -> src.copyTo(dst) } }
            "$PHOTOS_DIR/$fileName"
        } catch (e: IOException) {
            destFile.delete()
            null
        }
    }

    fun savePhotos(uris: List<Uri>): List<String> = uris.mapNotNull { savePhoto(it) }

    fun deletePhotos(relativePaths: List<String>) {
        relativePaths.forEach { File(context.filesDir, it).delete() }
    }

    companion object {
        const val PHOTOS_DIR = "note_photos"
    }
}
