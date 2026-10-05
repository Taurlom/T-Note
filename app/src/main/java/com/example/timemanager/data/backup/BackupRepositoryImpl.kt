package com.example.timemanager.data.backup

import android.content.Context
import android.net.Uri
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.local.AppDatabaseMigration
import com.example.timemanager.domain.repository.BackupDescription
import com.example.timemanager.domain.repository.BackupImportException
import com.example.timemanager.domain.repository.BackupRepository
import com.example.timemanager.domain.repository.BackupSummary
import com.example.timemanager.domain.repository.DocumentRepository
import com.example.timemanager.domain.repository.NoteRepository
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.ThemeKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Резервная копия всего пользовательского одним zip-архивом:
 *
 * ```
 * manifest.json              — формат, версия приложения, тема, шрифт,
 *                              счётчик фото и список отсутствовавших файлов
 * database/time_manager.db   — файл Room (WAL предварительно сливается в него)
 * document_photos/…          — фотографии документов (filesDir/document_photos)
 * note_photos/…              — фотографии заметок (filesDir/note_photos)
 * ```
 *
 * Надёжность — четыре правила:
 * 1. Распаковка только в cacheDir и с потолками на объём и число
 *    записей: zip-бомба не может занять диск гигабайтами.
 * 2. Копия проверяется целиком ДО касания живых данных: формат,
 *    SQLite-заголовок, версия схемы. Копия от более новой версии
 *    приложения отклоняется здесь, а не роняет Room при первом же
 *    открытии — когда старые данные уже потеряны.
 * 3. Подмена — «транзакция» из атомарных переименований: живое
 *    отодвигается, новое ставится на место, старое стирается только
 *    после полного успеха. Сбой любого шага откатывает сделанное.
 * 4. Неудавшийся экспорт удаляет недописанный файл: обрезок архива
 *    не должен выглядеть со стороны годной копией.
 *
 * Room и DataStore держат старые файлы открытыми, поэтому после
 * импорта приложение перезапускают (см. SettingsScreen).
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
    private val noteRepository: NoteRepository
) : BackupRepository {

    override suspend fun exportBackup(target: Uri): BackupSummary =
        withContext(Dispatchers.IO) {
            checkpointWal()

            // Самопроверка: каждый путь фото (документов и заметок) из базы
            // должен существовать на диске. Несуществующие в копию не попадут —
            // пользователь узнает об этом сразу (тост), а не после восстановления.
            val referenced = documentRepository.getAll().first().flatMap { it.photoPaths } +
                noteRepository.getAll().first().flatMap { it.photoPaths }
            val missing = referenced.filterNot { File(context.filesDir, it).exists() }
            val photoFiles = photosDir().walkTopDown().filter { it.isFile }.toList()
            val notePhotoFiles = notePhotosDir().walkTopDown().filter { it.isFile }.toList()
            // Сироты: файлы есть, ссылок нет — наследие старых гонок записи.
            val orphans =
                countOrphans(photoFiles, photosDir(), PHOTOS_DIR, referenced) +
                    countOrphans(notePhotoFiles, notePhotosDir(), NOTE_PHOTOS_DIR, referenced)

            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw IllegalStateException("Не удалось открыть файл для записи")
            try {
                stream.use { output ->
                    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                        zip.writeJson(
                            MANIFEST_ENTRY,
                            manifestJson(photoFiles.size, notePhotoFiles.size, missing, orphans)
                        )

                        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
                        if (dbFile.exists()) {
                            zip.writeFile("$DATABASE_DIR/${dbFile.name}", dbFile)
                        }

                        photoFiles.forEach { file ->
                            val relative = file.relativeTo(photosDir()).path
                            zip.writeFile("$PHOTOS_DIR/$relative", file)
                        }
                        notePhotoFiles.forEach { file ->
                            val relative = file.relativeTo(notePhotosDir()).path
                            zip.writeFile("$NOTE_PHOTOS_DIR/$relative", file)
                        }
                    }
                }
            } catch (t: Throwable) {
                // Обрезанный на середине архив остаётся в файловой системе
                // (файл выбран через «Сохранить как…») и со стороны выглядит
                // годной копией. Убираем недописанное, ошибку пробрасываем;
                // удаление — лучшее усилие: если SAF откажет, файл останется,
                // но о себе он уже не скажет ничего.
                // Классическая перегрузка с тремя аргументами: доступна
                // с API 1. Двухаргументная delete(Uri, Bundle) существует
                // только с API 30 — на Android 10 и ниже вызов падал
                // NoSuchMethodError (глотался runCatching), и обрезок
                // архива оставался на диске, выглядя годной копией.
                runCatching { context.contentResolver.delete(target, null, null) }
                throw t
            }
            BackupSummary(
                photos = photoFiles.size + notePhotoFiles.size,
                missingPhotos = missing
            )
        }

    override suspend fun describeBackup(source: Uri): BackupDescription? =
        withContext(Dispatchers.IO) {
            // null («не копия») — только когда манифеста нет или он не
            // разбирается. Отказ чтения — честная ошибка: сказать «не копия»
            // про недоступный файл значило бы ввести пользователя в
            // заблуждение.
            val manifest = try {
                readManifest(source)
            } catch (e: JSONException) {
                null
            }
            manifest?.let { meta ->
                BackupDescription(
                    createdAt = meta.optLong("createdAt"),
                    appVersion = meta.optString("appVersionName"),
                    photos = meta.optInt("photos"),
                    missingPhotos = meta.optJSONArray("missingPhotos")?.length() ?: 0
                )
            }
        }

    override suspend fun importBackup(source: Uri): BackupSummary =
        withContext(Dispatchers.IO) {
            val tempDir = File(context.cacheDir, IMPORT_TEMP_DIR)
                .apply { deleteRecursively(); mkdirs() }
            val photosOut = File(tempDir, PHOTOS_DIR).apply { mkdirs() }
            val notePhotosOut = File(tempDir, NOTE_PHOTOS_DIR).apply { mkdirs() }
            val dbOut = File(tempDir, AppDatabase.DB_NAME)
            var manifest: JSONObject? = null
            var restoredPhotos = 0

            try {
                val input = context.contentResolver.openInputStream(source)
                    ?: throw IllegalStateException("Не удалось открыть файл копии")
                input.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zip ->
                        var unpackedBytes = 0L
                        var entries = 0

                        /**
                         * Копия текущей entry в [out] с предохранителями:
                         * потолок [limit] на запись (база и фото не бывают
                         * настолько большими) и на архив целиком. Размеры в
                         * ZIP-заголовках необязательны и лгут — счёт ведётся
                         * по факту распаковки, отказ сразу, не распаковывая
                         * остальное.
                         */
                        fun copyEntry(out: OutputStream, limit: Long = MAX_ENTRY_BYTES) {
                            val buffer = ByteArray(IO_BUFFER_BYTES)
                            var entryBytes = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                unpackedBytes += read
                                if (entryBytes > limit ||
                                    unpackedBytes > MAX_TOTAL_BYTES
                                ) throw BackupImportException.TooLarge
                                out.write(buffer, 0, read)
                            }
                        }

                        while (true) {
                            val entry = zip.nextEntry ?: break
                            // Миллион пустых файлов — тоже бомба.
                            if (++entries > MAX_ENTRIES) {
                                throw BackupImportException.TooLarge
                            }
                            // Имена берём из недоверенного архива — не даём выйти
                            // за пределы временного каталога (zip slip).
                            val name = entry.name.trimStart('/')
                            when {
                                entry.isDirectory -> Unit
                                name == MANIFEST_ENTRY -> {
                                    // Манифест мал, но и его читаем с потолком:
                                    // архив не доверяем, а readBytes() позволил
                                    // бы распаковать гигабайт «в память».
                                    val bytes = ByteArrayOutputStream()
                                    copyEntry(bytes, MAX_MANIFEST_BYTES)
                                    manifest = runCatching {
                                        JSONObject(bytes.toByteArray().decodeToString())
                                    }.getOrNull()
                                }
                                name.startsWith("$DATABASE_DIR/") &&
                                    File(name).name == AppDatabase.DB_NAME ->
                                    // Стриминг на диск, без readBytes(): база
                                    // не обязана влезать в память процесса.
                                    dbOut.outputStream().buffered().use { copyEntry(it) }
                                name.startsWith("$PHOTOS_DIR/") &&
                                    File(name).name.isNotEmpty() -> {
                                    // Фото хранятся плоско; вложенность на всякий
                                    // случай схлопываем в имя файла.
                                    val file = File(
                                        photosOut,
                                        name.substringAfter("$PHOTOS_DIR/").replace('/', '_')
                                    )
                                    file.outputStream().buffered().use { copyEntry(it) }
                                    restoredPhotos++
                                }
                                name.startsWith("$NOTE_PHOTOS_DIR/") &&
                                    File(name).name.isNotEmpty() -> {
                                    val file = File(
                                        notePhotosOut,
                                        name.substringAfter("$NOTE_PHOTOS_DIR/").replace('/', '_')
                                    )
                                    file.outputStream().buffered().use { copyEntry(it) }
                                    restoredPhotos++
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                }

                // ---- Проверки ДО подмены: отказывать, пока живые данные целы.
                val meta = manifest ?: throw BackupImportException.NotABackup
                if (meta.optInt("formatVersion") != BACKUP_FORMAT_VERSION) {
                    throw BackupImportException.UnsupportedFormat(meta.optInt("formatVersion"))
                }
                if (!dbOut.exists() || dbOut.length() == 0L) {
                    throw BackupImportException.NoDatabase
                }
                // Версия схемы: Room хранит её в PRAGMA user_version файла БД.
                // Текущая — из живого подключения, а не константа: защита от
                // рассинхрона кода и реально открытой схемы.
                val currentSchema = database.openHelper.readableDatabase.version
                val backupSchema = SQLiteHeader.fileVersion(dbOut)
                when {
                    backupSchema == null || backupSchema == 0 ->
                        // Room никогда не пишет user_version = 0: файл битый
                        // или базой T-Note не является.
                        throw BackupImportException.InvalidDatabase
                    backupSchema > currentSchema ->
                        throw BackupImportException.NewerSchema(backupSchema, currentSchema)
                    backupSchema < AppDatabaseMigration.MIN_SUPPORTED_VERSION ->
                        // Цепочка миграций начинается с этой версии: более
                        // старую базу поднять нечем (destructive fallback
                        // отсутствует намеренно).
                        throw BackupImportException.UnsupportedSchema(
                            backupSchema,
                            AppDatabaseMigration.MIN_SUPPORTED_VERSION
                        )
                }

                // ---- «Транзакция» подмены. ----
                // Сначала сливаем WAL в основной файл: после этого -wal/-shm —
                // пустые хвосты прежней базы, новой они не нужны.
                checkpointWal()
                val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()

                // Выполненные подмены; откат — в обратном порядке.
                val applied = ArrayDeque<Swap>()

                /**
                 * Ставит [replacement] на место [target]: живое отодвигается
                 * в `*.import-old`, новое занимает место через временное имя
                 * `*.import-new` и одно атомарное переименование — под
                 * каноническим именем никогда не лежит частично записанный
                 * файл (иначе падение процесса в момент копирования = вечный
                 * краш на старте). Все каталоги — внутреннее хранилище,
                 * переименование атомарно в пределах одного тома.
                 */
                fun swap(target: File, replacement: File) {
                    val old = File(target.parentFile, target.name + IMPORT_OLD_SUFFIX)
                    val staged = File(target.parentFile, target.name + IMPORT_STAGING_SUFFIX)
                    // Хвост «жёстко» упавшего прошлого импорта (процесс убит
                    // между переименованиями): выравниваем — данные должны
                    // лежать в target. Его нет — old единственная копия,
                    // вернуть; оба есть — target завершён (переименование
                    // атомарно), old — мусор.
                    when {
                        old.exists() && !target.exists() ->
                            check(old.renameTo(target)) {
                                "Не удалось восстановить ${old.path} после прошлого импорта"
                            }
                        old.exists() -> old.deleteRecursively()
                    }
                    val hadTarget = target.exists()
                    if (hadTarget) {
                        check(target.renameTo(old)) { "Не удалось отодвинуть ${target.path}" }
                    }
                    applied.addLast(Swap(target, if (hadTarget) old else null, staged))
                    staged.deleteRecursively()
                    move(replacement, staged)
                    check(staged.renameTo(target)) { "Не удалось поставить ${target.path}" }
                }

                try {
                    swap(dbFile, dbOut)
                    swap(photosDir(), photosOut)
                    swap(notePhotosDir(), notePhotosOut)

                    // Настройки «из копии» кладём не файлом (DataStore держит
                    // его открытым), а через репозиторий: edit() ждёт записи
                    // до возврата.
                    settingsRepository.setSelectedTheme(
                        ThemeKind.fromName(meta.optString("theme"))
                    )
                    settingsRepository.setSelectedFont(
                        AppFont.fromName(meta.optString("font"))
                    )
                } catch (t: Throwable) {
                    // Откат «транзакции»: лучшее усилие — если и он не удался,
                    // прежние файлы остаются лежать в *.import-old, и их можно
                    // поднять руками; это лучше, чем терять их совсем.
                    while (applied.isNotEmpty()) applied.removeLast().rollback()
                    throw t
                }
                applied.forEach { it.discard() }

                BackupSummary(
                    photos = restoredPhotos,
                    missingPhotos = meta.optJSONArray("missingPhotos")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }
                    }.orEmpty()
                )
            } finally {
                // Распакованное не переживает импорт ни успехом (файлы уже
                // перенесены), ни отказом — иначе cacheDir копит гигабайты
                // до следующей попытки.
                tempDir.deleteRecursively()
            }
        }

    /** Читает только manifest.json, не распаковывая остальное. */
    private fun readManifest(source: Uri): JSONObject? {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IllegalStateException("Не удалось открыть файл копии")
        input.use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    if (entry.name.trimStart('/') == MANIFEST_ENTRY) {
                        val bytes = zip.readEntryLimited(MAX_MANIFEST_BYTES)
                        // Битый JSON — «не копия», а не ошибка чтения.
                        return runCatching { JSONObject(bytes.decodeToString()) }.getOrNull()
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Читает текущую entry целиком с потолком [limit]: манифест мал,
     * но архив не доверяем — читаем не всё, что он предлагает.
     */
    private fun ZipInputStream.readEntryLimited(limit: Long): ByteArray {
        val out = ByteArrayOutputStream(8 * 1024)
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw BackupImportException.TooLarge
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Перенос в пределах внутреннего хранилища: rename, при отказе — копия. */
    private fun move(source: File, target: File) {
        if (!source.renameTo(target)) source.copyTo(target, overwrite = true)
    }

    private fun photosDir(): File = File(context.filesDir, PHOTOS_DIR)

    private fun notePhotosDir(): File = File(context.filesDir, NOTE_PHOTOS_DIR)

    /** Файлы каталога, на которые нет ссылок в [referenced]. */
    private fun countOrphans(
        files: List<File>,
        dir: File,
        prefix: String,
        referenced: List<String>
    ): Int = files.count { file ->
        "$prefix/${file.relativeTo(dir).path}" !in referenced
    }

    /** Сливает WAL-журнал в основной файл базы, чтобы копия была полной. */
    private fun checkpointWal() {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { it.moveToFirst() }
    }

    private suspend fun manifestJson(
        photoCount: Int,
        notePhotoCount: Int,
        missing: List<String>,
        orphans: Int
    ): JSONObject {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        return JSONObject()
            .put("formatVersion", BACKUP_FORMAT_VERSION)
            .put("appVersionName", appVersion)
            .put("createdAt", System.currentTimeMillis())
            .put("photos", photoCount + notePhotoCount)
            .put("notePhotos", notePhotoCount)
            .put("missingPhotos", JSONArray(missing))
            .put("orphanPhotos", orphans)
            .put("theme", settingsRepository.selectedTheme.first().name)
            .put("font", settingsRepository.selectedFont.first().name)
    }

    private fun ZipOutputStream.writeJson(name: String, json: JSONObject) {
        putNextEntry(ZipEntry(name))
        write(json.toString(2).toByteArray())
        closeEntry()
    }

    /** Время записи = время файла на устройстве: история для разборов потерь. */
    private fun ZipOutputStream.writeFile(name: String, file: File) {
        putNextEntry(
            ZipEntry(name).apply { time = file.lastModified() }
        )
        file.inputStream().buffered().use { it.copyTo(this) }
        closeEntry()
    }

    /**
     * Выполненная подмена: [target] занят новым содержимым, [old] хранит
     * отодвинутое прежнее (null — его не было), [staged] — временное имя,
     * через которое новое встало на место.
     */
    private class Swap(
        private val target: File,
        private val old: File?,
        private val staged: File
    ) {

        /** Состояние до подмены; лучшее усилие — исключений не бросает. */
        fun rollback() {
            target.deleteRecursively()
            staged.deleteRecursively()
            old?.renameTo(target)
        }

        /** Подмена удалась — прежнее больше не нужно. */
        fun discard() {
            staged.deleteRecursively()
            old?.deleteRecursively()
        }
    }

    private companion object {
        const val BACKUP_FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATABASE_DIR = "database"
        const val PHOTOS_DIR = "document_photos"
        const val NOTE_PHOTOS_DIR = "note_photos"
        // Имя файла базы — AppDatabase.DB_NAME: Room открывает и копия
        // подменяет один и тот же файл, источник обязан быть один.
        const val IMPORT_TEMP_DIR = "backup_import"

        // Потолки распаковки недоверенного архива (zip-бомба: сжатые до
        // килобайтов данные распаковываются в гигабайты). Числа щедрые —
        // под тяжёлого пользователя с сотнями фото по несколько мегабайт —
        // но конечные: бомба обязана упереться в отказ, а не в диск.
        const val MAX_MANIFEST_BYTES = 1L shl 20 // 1 МБ
        const val MAX_ENTRY_BYTES = 256L shl 20 // 256 МБ на запись
        const val MAX_TOTAL_BYTES = 2048L shl 20 // 2 ГБ на архив
        const val MAX_ENTRIES = 100_000
        const val IO_BUFFER_BYTES = 64 * 1024

        const val IMPORT_OLD_SUFFIX = ".import-old"
        const val IMPORT_STAGING_SUFFIX = ".import-new"
    }
}
