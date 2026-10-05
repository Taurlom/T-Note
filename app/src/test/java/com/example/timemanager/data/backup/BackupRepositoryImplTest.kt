package com.example.timemanager.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.local.AppDatabaseMigration
import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.model.ThemeKind
import com.example.timemanager.domain.repository.BackupImportException
import com.example.timemanager.domain.repository.DocumentRepository
import com.example.timemanager.domain.repository.NoteRepository
import com.example.timemanager.domain.repository.SettingsRepository
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Интеграционные тесты резервных копий на JVM (Robolectric): реальный
 * контекст (filesDir/cacheDir/ContentResolver), реальный Room-инстанс
 * как «живая» база и фейки трёх доменных репозиториев на их интерфейсах.
 * Байты базы — рукотворные SQLite-заголовки: репозиторию файл базы
 * непрозрачен, валидация заголовка уже покрыта SQLiteHeaderTest.
 *
 * Имена записей архива, суффиксы подмены (.import-old/.import-new) и
 * имя временного каталога захардкожены как литералы: это формат
 * хранения, переживающий перезапуски процесса, — тест обязан его
 * застолбить, а не подстраиваться за константами реализации.
 *
 * Проверяются четыре правила из шапки BackupRepositoryImpl:
 * лимиты распаковки (zip-бомба), проверка ДО подмены (версия схемы),
 * транзакция подмены с откатом, удаление недописанного экспорта.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryImplTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * «Живая» база как в проде: реальный Room реальной схемы — импорт
     * спрашивает у открытого подключения текущую версию схемы и делает
     * wal_checkpoint, ин-мемори достаточно и для того, и для другого
     * (подменяется всё равно файл по каноническому пути, а не инстанс).
     */
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val settings = FakeSettingsRepository()
    private val documents = FakeDocumentRepository()
    private val notes = FakeNoteRepository()

    private val repository = BackupRepositoryImpl(
        context, database, settings, documents, notes
    )

    /** Схема, о которой отчитывается открытое подключение (сейчас 16). */
    private val currentSchema = database.openHelper.readableDatabase.version

    @After
    fun tearDown() {
        database.close()
    }

    // ------------------------------------------------------------------
    // Экспорт
    // ------------------------------------------------------------------

    @Test
    fun `export packs database photos and manifest, describe reads it back`() {
        val dbBytes = sqliteBytes(currentSchema, "live-db")
        seedLiveState(
            dbBytes,
            docPhotos = mapOf("a.jpg" to "doc-a", "orphan.jpg" to "orphan"),
            notePhotos = mapOf("n.jpg" to "note-n")
        )
        // База ссылается на a.jpg и несуществующий lost.jpg; orphan.jpg
        // на диске есть, но ссылок на него нет.
        documents.items = listOf(
            Document(photoPaths = listOf("$DOC_PHOTOS/a.jpg", "$DOC_PHOTOS/lost.jpg"))
        )
        notes.items = listOf(Note(title = "n", photoPaths = listOf("$NOTE_PHOTOS/n.jpg")))

        val target = File(context.cacheDir, "exported.zip")
        val summary = runBlocking { repository.exportBackup(Uri.fromFile(target)) }

        // Три файла попали в копию (включая сироту); потеря названа по имени.
        assertEquals(3, summary.photos)
        assertEquals(listOf("$DOC_PHOTOS/lost.jpg"), summary.missingPhotos)

        val entries = zipEntries(target)
        assertArrayEquals(dbBytes, entries[DB_ENTRY])
        assertEquals("doc-a", entries["$DOC_PHOTOS/a.jpg"]?.decodeToString())
        assertEquals("note-n", entries["$NOTE_PHOTOS/n.jpg"]?.decodeToString())

        val manifest = JSONObject(entries[MANIFEST]!!.decodeToString())
        assertEquals(1, manifest.getInt("formatVersion"))
        assertEquals(3, manifest.getInt("photos"))
        assertEquals(1, manifest.getInt("orphanPhotos"))
        assertEquals("DARK", manifest.getString("theme"))

        val description = runBlocking { repository.describeBackup(Uri.fromFile(target)) }
        assertNotNull(description)
        assertEquals(3, description!!.photos)
        assertEquals(1, description.missingPhotos)
        assertTrue(description.appVersion.isNotEmpty())
        assertTrue(description.createdAt > 0)
    }

    @Test
    fun `failed export attempts to delete the half-written target`() {
        seedLiveState()
        // Чтение настроек взрывается ПОСЛЕ открытия потока записи —
        // файл уже создан, но манифест не дописан: ровно состояние
        // «архив-обрезок», которое экспорт обязан убрать за собой.
        settings.explodeOnRead = true
        val target = File(context.cacheDir, "exported.zip")
        val targetUri = Uri.fromFile(target)

        failsWith<IllegalStateException> {
            runBlocking { repository.exportBackup(targetUri) }
        }

        // Физически удалить file://-цель Robolectric не умеет: delete()
        // в тени — no-op (на устройстве файл стирает SAF-провайдер
        // content://-Uri). Закрепляем проверяемую часть контракта —
        // сам вызов delete с правильным Uri после сбоя записи.
        @Suppress("DEPRECATION") // трекинг delete-вызовов — ровно наш случай
        val deleted = shadowOf(context.contentResolver).deletedUris
        assertEquals(listOf(targetUri), deleted)
    }

    // ------------------------------------------------------------------
    // Импорт: счастливый путь
    // ------------------------------------------------------------------

    @Test
    fun `import replaces live files and applies settings from the manifest`() {
        seedLiveState(
            docPhotos = mapOf("old.jpg" to "old-doc"),
            notePhotos = mapOf("old-note.jpg" to "old-note")
        )
        val backupDb = sqliteBytes(currentSchema, "backup-db")
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to backupDb,
            "$DOC_PHOTOS/new.jpg" to "new-doc".toByteArray(),
            "$NOTE_PHOTOS/n.jpg" to "new-note".toByteArray()
        )

        val summary = runBlocking { repository.importBackup(source) }

        assertEquals(2, summary.photos)
        // База заменена побайтово.
        assertArrayEquals(backupDb, liveDbFile().readBytes())
        // Каталоги фото заменены ЦЕЛИКОМ: прежних файлов не осталось.
        assertEquals(setOf("new.jpg"), docPhotosDir().list()!!.toSet())
        assertEquals("new-doc", File(docPhotosDir(), "new.jpg").readText())
        assertEquals(setOf("n.jpg"), notePhotosDir().list()!!.toSet())
        assertEquals("new-note", File(notePhotosDir(), "n.jpg").readText())
        // Тема и шрифт применены через репозиторий, а не файлом.
        assertEquals(ThemeKind.OCEAN, settings.theme.value)
        assertEquals(AppFont.RUBIK, settings.font.value)
        assertNoSwapLeftovers()
        assertFalse(File(context.cacheDir, "backup_import").exists())
    }

    // ------------------------------------------------------------------
    // Импорт: отказ ДО подмены — живые данные не тронуты
    // ------------------------------------------------------------------

    @Test
    fun `archive without manifest is not a backup and changes nothing`() {
        seedLiveState(
            docPhotos = mapOf("old.jpg" to "old"),
            notePhotos = mapOf("n.jpg" to "note")
        )
        val before = liveSnapshot()
        val source = backupZip(DB_ENTRY to sqliteBytes(currentSchema, "db"))

        assertNull(runBlocking { repository.describeBackup(source) })
        failsWith<BackupImportException.NotABackup> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
        assertNoSwapLeftovers()
    }

    @Test
    fun `unknown manifest format version is rejected before the swap`() {
        seedLiveState()
        val before = liveSnapshot()
        val source = backupZip(
            MANIFEST to manifest(formatVersion = 2),
            DB_ENTRY to sqliteBytes(currentSchema, "db")
        )

        failsWith<BackupImportException.UnsupportedFormat> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
    }

    @Test
    fun `backup without database entry is rejected`() {
        seedLiveState()
        val before = liveSnapshot()
        val source = backupZip(
            MANIFEST to manifest(),
            "$DOC_PHOTOS/a.jpg" to "a".toByteArray()
        )

        failsWith<BackupImportException.NoDatabase> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
    }

    @Test
    fun `database file with broken sqlite header is rejected`() {
        seedLiveState()
        val before = liveSnapshot()
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to "definitely not sqlite".toByteArray()
        )

        failsWith<BackupImportException.InvalidDatabase> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
    }

    @Test
    fun `schema newer than the live database is rejected with both versions`() {
        seedLiveState()
        val before = liveSnapshot()
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to sqliteBytes(currentSchema + 1, "from-the-future")
        )

        val error = failsWith<BackupImportException.NewerSchema> {
            runBlocking { repository.importBackup(source) }
        }
        // Сообщение для логов называет обе версии: найденную и текущую.
        assertNotNull(error.message)
        assertTrue(error.message!!.contains("${currentSchema + 1}"))
        assertTrue(error.message!!.contains("$currentSchema"))
        assertEquals(before, liveSnapshot())
    }

    @Test
    fun `schema older than the migration chain start is rejected`() {
        seedLiveState()
        val before = liveSnapshot()
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to sqliteBytes(AppDatabaseMigration.MIN_SUPPORTED_VERSION - 1, "ancient")
        )

        failsWith<BackupImportException.UnsupportedSchema> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
    }

    // ------------------------------------------------------------------
    // Импорт: лимиты распаковки (zip-бомба)
    // ------------------------------------------------------------------

    @Test
    fun `oversized manifest entry is rejected by the unpack limit`() {
        seedLiveState()
        val before = liveSnapshot()
        // 2 МБ в манифесте — потолок 1 МБ; размер в ZIP-заголовке при
        // этом честный, отказ приходит по факту распаковки.
        val source = backupZip(MANIFEST to ByteArray(2 * 1024 * 1024))

        failsWith<BackupImportException.TooLarge> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
        assertNoSwapLeftovers()
        assertFalse(File(context.cacheDir, "backup_import").exists())
    }

    @Test
    fun `archive with more entries than the limit is rejected`() {
        seedLiveState()
        val before = liveSnapshot()
        // Миллион пустых файлов — тоже бомба: не объёмом, так счётчиком
        // записей. 100 000 — предел распаковщика.
        val source = manyEntriesZip(count = 100_001)

        failsWith<BackupImportException.TooLarge> {
            runBlocking { repository.importBackup(source) }
        }
        assertEquals(before, liveSnapshot())
    }

    // ------------------------------------------------------------------
    // Импорт: имена записей из недоверенного архива (zip slip)
    // ------------------------------------------------------------------

    @Test
    fun `zip slip entry names are flattened inside the photo dirs`() {
        seedLiveState()
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to sqliteBytes(currentSchema, "db"),
            "$DOC_PHOTOS/../../escaped-doc.jpg" to "evil".toByteArray(),
            "$NOTE_PHOTOS/../../../escaped-note.jpg" to "evil".toByteArray()
        )

        // Импорт не падает: злонамеренные имена не ломают распаковку…
        runBlocking { repository.importBackup(source) }

        // …но выпрямляются в безобидные имена ФАЙЛОВ внутри каталогов
        // фото (слеши заменяются на подчёркивания).
        assertTrue(File(docPhotosDir(), ".._.._escaped-doc.jpg").isFile)
        assertTrue(File(notePhotosDir(), ".._.._.._escaped-note.jpg").isFile)
        // Ничего не вырвалось наружу — ни в корень filesDir, ни выше.
        assertFalse(File(context.filesDir, "escaped-doc.jpg").exists())
        assertFalse(File(context.filesDir, "escaped-note.jpg").exists())
        assertFalse(File(context.filesDir.parentFile, "escaped-doc.jpg").exists())
        assertFalse(File(context.filesDir.parentFile, "escaped-note.jpg").exists())
    }

    // ------------------------------------------------------------------
    // Транзакция подмены
    // ------------------------------------------------------------------

    @Test
    fun `failure after the swap rolls live data back byte for byte`() {
        seedLiveState(
            docPhotos = mapOf("old.jpg" to "old-doc"),
            notePhotos = mapOf("old-note.jpg" to "old-note")
        )
        val before = liveSnapshot()
        // Подмена файлов уже прошла, а применение настроек падает —
        // ровно середина «транзакции», из которой обязан быть откат.
        settings.explodeOnApplyTheme = true
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to sqliteBytes(currentSchema, "backup-db"),
            "$DOC_PHOTOS/new.jpg" to "new".toByteArray(),
            "$NOTE_PHOTOS/n.jpg" to "n".toByteArray()
        )

        failsWith<IllegalStateException> {
            runBlocking { repository.importBackup(source) }
        }

        // Прежняя база и прежние каталоги фото восстановлены полностью.
        assertEquals(before, liveSnapshot())
        assertNoSwapLeftovers()
        assertFalse(File(context.cacheDir, "backup_import").exists())
    }

    @Test
    fun `import recovers from a previously crashed swap`() {
        seedLiveState()
        // Хвост импорта, убитого процессом между переименованиями:
        // живая база отодвинута в .import-old, канонического файла нет.
        val crashed = File(liveDbFile().path + ".import-old")
        assertTrue(liveDbFile().renameTo(crashed))

        val backupDb = sqliteBytes(currentSchema, "backup-db")
        val source = backupZip(MANIFEST to manifest(), DB_ENTRY to backupDb)

        runBlocking { repository.importBackup(source) }

        // Новая база на месте, хвост прошлого импорта вычищен
        // (выравнивание в swap() + discard после успеха).
        assertArrayEquals(backupDb, liveDbFile().readBytes())
        assertNoSwapLeftovers()
    }

    // ------------------------------------------------------------------
    // describeBackup: «не копия» против ошибки чтения
    // ------------------------------------------------------------------

    @Test
    fun `describeBackup separates not-a-backup from an unreadable file`() {
        // Нет манифеста — «не копия» (null), а не ошибка.
        assertNull(
            runBlocking {
                repository.describeBackup(backupZip(DB_ENTRY to sqliteBytes(currentSchema, "db")))
            }
        )
        // Битый JSON в манифесте — тоже «не копия».
        assertNull(
            runBlocking {
                repository.describeBackup(backupZip(MANIFEST to "not json{{".toByteArray()))
            }
        )
        // Недоступный файл — честная ошибка: сказать «не копия» про
        // файл, который не удалось открыть, значило бы соврать.
        val missing = Uri.fromFile(File(context.cacheDir, "missing.zip"))
        val error = runBlocking { runCatching { repository.describeBackup(missing) }.exceptionOrNull() }
        assertNotNull(error)
        // Для импорта битый манифест — тот же отказ, что и отсутствие.
        failsWith<BackupImportException.NotABackup> {
            runBlocking { repository.importBackup(backupZip(MANIFEST to "not json{{".toByteArray())) }
        }
    }

    // ------------------------------------------------------------------
    // Разделы нижней панели в копии
    // ------------------------------------------------------------------

    @Test
    fun `visible sections roundtrip through export and import`() {
        settings.sections.value = listOf("notes", "tasks", "calendar")
        seedLiveState()
        val target = File(context.cacheDir, "roundtrip.zip")

        runBlocking { repository.exportBackup(Uri.fromFile(target)) }

        // Порядок разделов упакован в манифест.
        val packed = JSONObject(zipEntries(target)[MANIFEST]!!.decodeToString())
            .optJSONArray("visibleSections")!!
        assertEquals(
            listOf("notes", "tasks", "calendar"),
            (0 until packed.length()).map { packed.optString(it) }
        )

        runBlocking { repository.importBackup(Uri.fromFile(target)) }

        // И восстановлен через репозиторий, а не файлом DataStore.
        assertEquals(
            listOf(listOf("notes", "tasks", "calendar")),
            settings.appliedSections
        )
        assertEquals(listOf("notes", "tasks", "calendar"), settings.sections.value)
    }

    @Test
    fun `manifest without visible sections keeps the current setting`() {
        seedLiveState()
        settings.sections.value = listOf("tasks")
        // manifest() не пишет visibleSections — формат копий 1.15.0.
        val source = backupZip(
            MANIFEST to manifest(),
            DB_ENTRY to sqliteBytes(currentSchema, "db")
        )

        runBlocking { repository.importBackup(source) }

        // Старая копия: настройку устройства не трогали — ни вызова,
        // ни изменения значения.
        assertTrue(settings.appliedSections.isEmpty())
        assertEquals(listOf("tasks"), settings.sections.value)
    }

    // ------------------------------------------------------------------
    // Инструменты
    // ------------------------------------------------------------------

    /**
     * Проверяет, что [block] падает с [T], и возвращает исключение.
     * assertThrows из JUnit не подходит: его перегрузки
     * (ThrowingRunnable/ThrowingSupplier) не дают Kotlin вывести тип
     * лямбды, у которой тело возвращает значение.
     */
    private inline fun <reified T : Throwable> failsWith(block: () -> Unit): T {
        val thrown: Throwable? = try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull(
            "Ожидалась ошибка ${T::class.simpleName}, но блок завершился успешно",
            thrown
        )
        assertTrue(
            "Ожидалась ${T::class.simpleName}, пришла ${thrown!!.javaClass.simpleName}",
            thrown is T
        )
        return thrown as T
    }

    private fun liveDbFile(): File = context.getDatabasePath("time_manager.db")

    private fun docPhotosDir(): File = File(context.filesDir, DOC_PHOTOS)

    private fun notePhotosDir(): File = File(context.filesDir, NOTE_PHOTOS)

    /**
     * Снимок живого состояния «до/после»: база + оба каталога фото.
     * Байты кодируются (Base64/UTF-8): массивы в data class-е и Map
     * сравнивались бы по ссылкам, а не по содержимому.
     */
    private data class LiveState(
        val db: String,
        val docPhotos: Map<String, String>,
        val notePhotos: Map<String, String>
    )

    private fun liveSnapshot(): LiveState = LiveState(
        db = Base64.getEncoder().encodeToString(liveDbFile().readBytes()),
        docPhotos = docPhotosDir().listFiles()!!.associate { it.name to it.readBytes().decodeToString() },
        notePhotos = notePhotosDir().listFiles()!!.associate { it.name to it.readBytes().decodeToString() }
    )

    /** Живые файлы на месте: база побайтово, состав фото не изменился. */
    private fun seedLiveState(
        dbBytes: ByteArray = sqliteBytes(currentSchema, "live-db"),
        docPhotos: Map<String, String> = emptyMap(),
        notePhotos: Map<String, String> = emptyMap()
    ) {
        liveDbFile().parentFile!!.mkdirs()
        liveDbFile().writeBytes(dbBytes)
        docPhotosDir().mkdirs()
        notePhotosDir().mkdirs()
        docPhotos.forEach { (name, content) -> File(docPhotosDir(), name).writeText(content) }
        notePhotos.forEach { (name, content) -> File(notePhotosDir(), name).writeText(content) }
    }

    /** SQLite-файл-заглушка: валидный заголовок + различимый маркер. */
    private fun sqliteBytes(version: Int, marker: String): ByteArray {
        // Формат продублирован из SQLiteHeaderTest: смещения известны
        // формату, а не реализации.
        val header = ByteArray(100)
        "SQLite format 3\u0000".toByteArray().copyInto(header)
        header[60] = (version ushr 24).toByte()
        header[61] = (version ushr 16).toByte()
        header[62] = (version ushr 8).toByte()
        header[63] = version.toByte()
        return header + marker.toByteArray()
    }

    /** Копия с валидным манифестом; тема и шрифт отличимы от дефолтных. */
    private fun manifest(
        formatVersion: Int = 1,
        theme: String = ThemeKind.OCEAN.name,
        font: String = AppFont.RUBIK.name
    ): ByteArray = JSONObject()
        .put("formatVersion", formatVersion)
        .put("appVersionName", "1.15.0-test")
        .put("createdAt", 123_456L)
        .put("photos", 2)
        .put("notePhotos", 1)
        .put("missingPhotos", JSONArray())
        .put("orphanPhotos", 0)
        .put("theme", theme)
        .put("font", font)
        .toString(2)
        .toByteArray()

    /** Архив-копия из записей «имя → байты». */
    private fun backupZip(vararg entries: Pair<String, ByteArray>): Uri {
        val file = File(context.cacheDir, "backup.zip")
        file.delete()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    /** Архив-бомба по СЧЁТЧИКУ записей: `count` пустых файлов. */
    private fun manyEntriesZip(count: Int): Uri {
        val file = File(context.cacheDir, "entries-bomb.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            repeat(count) { i ->
                zip.putNextEntry(ZipEntry("junk/entry-$i"))
                zip.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    /** Записи архива «имя → байты» — для проверки содержимого экспорта. */
    private fun zipEntries(file: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) result[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return result
    }

    /**
     * После импорта (успех или отказ) не остаётся ни отодвинутых
     * (*.import-old), ни недопоставленных (*.import-new) файлов —
     * иначе следующий импорт стартует из грязного состояния.
     */
    private fun assertNoSwapLeftovers() {
        val roots = listOfNotNull(
            context.filesDir,
            liveDbFile().parentFile?.takeIf { it.exists() }
        )
        roots.flatMap { it.walkTopDown().toList() }
            .filter { it.name.endsWith(".import-old") || it.name.endsWith(".import-new") }
            .forEach { fail("Остался временный файл подмены: ${it.path}") }
    }

    // ------------------------------------------------------------------
    // Фейки на интерфейсах домена: backup нужен только getAll/get/set,
    // остальное падает громко, если тесты вдруг попросят лишнего.
    // ------------------------------------------------------------------

    private class FakeSettingsRepository : SettingsRepository {
        val theme = MutableStateFlow(ThemeKind.DARK)
        val font = MutableStateFlow(AppFont.PT_SANS)

        /** Экспорт падает при чтении темы — после открытия потока записи. */
        var explodeOnRead = false

        /** Импорт падает при применении темы — после подмены файлов. */
        var explodeOnApplyTheme = false

        override val selectedTheme: Flow<ThemeKind>
            get() = if (explodeOnRead) {
                kotlinx.coroutines.flow.flow { throw IllegalStateException("theme read failed") }
            } else {
                theme
            }

        override val selectedFont: Flow<AppFont> = font

        override suspend fun setSelectedTheme(theme: ThemeKind) {
            if (explodeOnApplyTheme) throw IllegalStateException("theme apply failed")
            this.theme.value = theme
        }

        override suspend fun setSelectedFont(font: AppFont) {
            this.font.value = font
        }

        val sections = MutableStateFlow<List<String>>(emptyList())

        /** Вызовы setVisibleSections: что именно импорт применил из манифеста. */
        val appliedSections = mutableListOf<List<String>>()

        override val visibleSections: Flow<List<String>> = sections

        override suspend fun setVisibleSections(sections: List<String>) {
            appliedSections.add(sections)
            this.sections.value = sections
        }
    }

    private class FakeDocumentRepository : DocumentRepository {
        var items: List<Document> = emptyList()

        override fun getAll(): Flow<List<Document>> = flowOf(items)

        override fun getById(id: Long): Flow<Document?> =
            error("не используется тестами копий")

        override suspend fun getMaxPosition(): Int =
            error("не используется тестами копий")

        override suspend fun insert(document: Document): Long =
            error("не используется тестами копий")

        override suspend fun update(document: Document) =
            error("не используется тестами копий")

        override suspend fun updatePositions(documents: List<Document>) =
            error("не используется тестами копий")

        override suspend fun delete(document: Document) =
            error("не используется тестами копий")

        override suspend fun deleteAll() =
            error("не используется тестами копий")
    }

    private class FakeNoteRepository : NoteRepository {
        var items: List<Note> = emptyList()

        override fun getAll(): Flow<List<Note>> = flowOf(items)

        override fun getById(id: Long): Flow<Note?> =
            error("не используется тестами копий")

        override suspend fun getByIdOnce(id: Long): Note? =
            error("не используется тестами копий")

        override suspend fun getMaxPosition(): Int =
            error("не используется тестами копий")

        override suspend fun insert(note: Note): Long =
            error("не используется тестами копий")

        override suspend fun update(note: Note) =
            error("не используется тестами копий")

        override suspend fun updatePositions(notes: List<Note>) =
            error("не используется тестами копий")

        override suspend fun delete(note: Note) =
            error("не используется тестами копий")

        override suspend fun deleteAll() =
            error("не используется тестами копий")
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val DB_ENTRY = "database/time_manager.db"
        const val DOC_PHOTOS = "document_photos"
        const val NOTE_PHOTOS = "note_photos"
    }
}
