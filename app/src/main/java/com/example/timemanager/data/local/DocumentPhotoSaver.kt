package com.example.timemanager.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentPhotoSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun savePhoto(sourceUri: Uri?): String? {
        if (sourceUri == null) return null

        val photosDir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val fileName = "document_${UUID.randomUUID()}.jpg"
        val destFile = File(photosDir, fileName)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return "${PHOTOS_DIR}/$fileName"
    }

    fun savePhotos(uris: List<Uri>): List<String> = uris.mapNotNull { savePhoto(it) }

    fun deletePhoto(relativePath: String?) {
        if (relativePath == null) return
        File(context.filesDir, relativePath).delete()
    }

    fun deletePhotos(relativePaths: List<String?>) {
        relativePaths.filterNotNull().forEach { deletePhoto(it) }
    }

    companion object {
        private const val PHOTOS_DIR = "document_photos"
    }
}
