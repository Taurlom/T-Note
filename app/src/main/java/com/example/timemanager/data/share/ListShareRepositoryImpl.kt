package com.example.timemanager.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.timemanager.domain.model.ListShareFormat
import com.example.timemanager.domain.model.SharedList
import com.example.timemanager.domain.repository.ListShareRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ListShareRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ListShareRepository {

    override suspend fun exportToFile(sharedList: SharedList): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            // Прошлые экспорты не нужны: URI живёт до отправки интентом.
            dir.listFiles()?.forEach { it.delete() }

            val file =
                File(dir, sanitizeFileName(sharedList.name) + ListShareFormat.EXTENSION)
            file.writeText(ListShareCodec.encode(sharedList))
            FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
                file
            )
        }

    override suspend fun importFromFile(uri: Uri): SharedList =
        withContext(Dispatchers.IO) {
            // Размер проверяем до чтения: файл приходит из чужого приложения.
            val statSize = context.contentResolver
                .openFileDescriptor(uri, "r")
                ?.use { it.statSize }
            require(statSize == null || statSize < 0 || statSize <= MAX_FILE_BYTES) {
                "Файл слишком большой"
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            } ?: throw IllegalStateException("Файл недоступен для чтения")
            require(raw.length <= MAX_FILE_BYTES) { "Файл слишком большой" }
            ListShareCodec.decode(raw)
        }
}

/** Имя файла для мессенджеров: буквы/цифры/пробелы, остальное — подчёркивания. */
internal fun sanitizeFileName(name: String): String =
    name.trim()
        .replace(Regex("[^\\p{L}\\p{N} ._-]+"), "")
        .replace(Regex("\\s+"), "_")
        .trim('_')
        .take(60)
        .ifBlank { "tnote-list" }

private const val SHARE_DIR = "list_shares"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
private const val MAX_FILE_BYTES = 2_000_000L
