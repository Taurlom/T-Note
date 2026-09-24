package com.example.timemanager.data.backup

import android.content.Context
import android.net.Uri
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.domain.repository.BackupDescription
import com.example.timemanager.domain.repository.BackupRepository
import com.example.timemanager.domain.repository.BackupSummary
import com.example.timemanager.domain.repository.DocumentRepository
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.ThemeKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия всего пользовательского одним zip-архивом:
 *
 * ```
 * manifest.json              — формат, версия приложения, тема, шрифт,
 *                              счётчик фото и список отсутствовавших файлов
 * database/time_manager.db   — файл Room (WAL предварительно сливается в него)
 * document_photos/…          — фотографии документов (filesDir/document_photos)
 * ```
 *
 * Импорт распаковывается во временный каталог и подменяет файлы на месте;
 * Room и DataStore держат старые файлы открытыми, поэтому после импорта
 * приложение перезапускают (см. SettingsScreen).
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository
) : BackupRepository {

    override suspend fun exportBackup(target: Uri): BackupSummary =
        withContext(Dispatchers.IO) {
            checkpointWal()

            // Самопроверка: каждый путь фото из базы должен существовать на
            // диске. Несуществующие в копию не попадут — пользователь узнает
            // об этом сразу (тост), а не после восстановления.
            val referenced = documentRepository.getAll().first()
                .flatMap { it.photoPaths }
            val missing = referenced.filterNot { File(context.filesDir, it).exists() }
            val photoFiles = photosDir().walkTopDown().filter { it.isFile }.toList()

            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw IllegalStateException("Не удалось открыть файл для записи")
            stream.use { output ->
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.writeJson(MANIFEST_ENTRY, manifestJson(photoFiles.size, missing))

                    val dbFile = context.getDatabasePath(DB_NAME)
                    if (dbFile.exists()) {
                        zip.writeFile("$DATABASE_DIR/${dbFile.name}", dbFile)
                    }

                    photoFiles.forEach { file ->
                        val relative = file.relativeTo(photosDir()).path
                        zip.writeFile("$PHOTOS_DIR/$relative", file)
                    }
                }
            }
            BackupSummary(photos = photoFiles.size, missingPhotos = missing)
        }

    override suspend fun describeBackup(source: Uri): BackupDescription? =
        withContext(Dispatchers.IO) {
            runCatching {
                readManifest(source)?.let { manifest ->
                    BackupDescription(
                        createdAt = manifest.optLong("createdAt"),
                        appVersion = manifest.optString("appVersionName"),
                        photos = manifest.optInt("photos"),
                        missingPhotos = manifest.optJSONArray("missingPhotos")?.length() ?: 0
                    )
                }
            }.getOrNull()
        }

    override suspend fun importBackup(source: Uri): BackupSummary =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, IMPORT_TEMP_DIR)
                .apply { deleteRecursively(); mkdirs() }
            val photosOut = File(tempDir, PHOTOS_DIR).apply { mkdirs() }
            val dbOut = File(tempDir, DB_NAME)
            var manifest: JSONObject? = null
            var restoredPhotos = 0

            val input = context.contentResolver.openInputStream(source)
                ?: throw IllegalStateException("Не удалось открыть файл копии")
            input.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        // Имена берём из недоверенного архива — не даём выйти
                        // за пределы временного каталога (zip slip).
                        val name = entry.name.trimStart('/')
                        when {
                            entry.isDirectory -> Unit
                            name == MANIFEST_ENTRY ->
                                manifest = JSONObject(zip.readBytes().decodeToString())
                            name.startsWith("$DATABASE_DIR/") &&
                                File(name).name == DB_NAME ->
                                zip.readBytes().let { dbOut.writeBytes(it) }
                            name.startsWith("$PHOTOS_DIR/") &&
                                File(name).name.isNotEmpty() -> {
                                // Фото хранятся плоско; вложенность на всякий
                                // случай схлопываем в имя файла.
                                val file = File(
                                    photosOut,
                                    name.substringAfter("$PHOTOS_DIR/").replace('/', '_')
                                )
                                file.outputStream().use { zip.copyTo(it) }
                                restoredPhotos++
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }

            val meta = requireNotNull(manifest) {
                "Файл не является резервной копией T-Note"
            }
            check(meta.optInt("formatVersion") == BACKUP_FORMAT_VERSION) {
                "Неподдерживаемый формат копии"
            }
            check(dbOut.exists() && dbOut.length() > 0) {
                "В копии нет базы данных"
            }

            // Старые WAL/SHM могут относиться к прежней базе — удаляем вместе с ней.
            checkpointWal()
            val dbFile = context.getDatabasePath(DB_NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            dbOut.copyTo(dbFile, overwrite = true)

            val photos = photosDir()
            photos.deleteRecursively()
            photosOut.copyRecursively(photos, overwrite = true)

            // Настройки «из копии» кладём не файлом (DataStore держит его открытым),
            // а через репозиторий: edit() ждёт записи до возврата.
            settingsRepository.setSelectedTheme(ThemeKind.fromName(meta.optString("theme")))
            settingsRepository.setSelectedFont(AppFont.fromName(meta.optString("font")))

            tempDir.deleteRecursively()
            BackupSummary(
                photos = restoredPhotos,
                missingPhotos = meta.optJSONArray("missingPhotos")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                }.orEmpty()
            )
        }

    /** Читает только manifest.json, не распаковывая остальное. */
    private fun readManifest(source: Uri): JSONObject? {
        val input = context.contentResolver.openInputStream(source) ?: return null
        input.use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    if (entry.name.trimStart('/') == MANIFEST_ENTRY) {
                        return JSONObject(zip.readBytes().decodeToString())
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun photosDir(): File = File(context.filesDir, PHOTOS_DIR)

    /** Сливает WAL-журнал в основной файл базы, чтобы копия была полной. */
    private fun checkpointWal() {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { it.moveToFirst() }
    }

    private suspend fun manifestJson(photoCount: Int, missing: List<String>): JSONObject {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        return JSONObject()
            .put("formatVersion", BACKUP_FORMAT_VERSION)
            .put("appVersionName", appVersion)
            .put("createdAt", System.currentTimeMillis())
            .put("photos", photoCount)
            .put("missingPhotos", JSONArray(missing))
            .put("theme", settingsRepository.selectedTheme.first().name)
            .put("font", settingsRepository.selectedFont.first().name)
    }

    private fun ZipOutputStream.writeJson(name: String, json: JSONObject) {
        putNextEntry(ZipEntry(name))
        write(json.toString(2).toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.writeFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(this) }
        closeEntry()
    }

    private companion object {
        const val BACKUP_FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATABASE_DIR = "database"
        const val PHOTOS_DIR = "document_photos"
        const val DB_NAME = "time_manager.db"
        const val IMPORT_TEMP_DIR = "backup_import"
    }
}
