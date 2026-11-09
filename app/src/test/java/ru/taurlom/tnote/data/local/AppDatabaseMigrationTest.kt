package ru.taurlom.tnote.data.local

import android.content.Context
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Тесты миграций Room на JVM (Robolectric). Старые схемы читаются из
 * app/schemas — поэтому протестировать можно только переходы с версий,
 * для которых схема экспортирована (экспорт включён начиная с 16).
 *
 * Шаблон для будущей миграции 18 -> 19:
 * ```
 * helper.createDatabase(18).apply { /* наполнить старыми данными */ close() }
 * helper.runMigrationsAndValidate(19, listOf(AppDatabaseMigration.MIGRATION_18_19))
 * ```
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val dbFile = File(
        ApplicationProvider.getApplicationContext<Context>().cacheDir,
        "migration-test.db",
    )

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        // Driver-API вместо legacy SupportSQLiteOpenHelper: SupportSQLiteDriver
        // при проверке имени файла режет путь по '/', что ломается на
        // Windows-путях с '\'. AndroidSQLiteDriver открывает файл напрямую.
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
        autoMigrationSpecs = emptyList<AutoMigrationSpec>(),
    )

    /**
     * Базовая проверка: база текущей версии, созданная по экспортированной
     * схеме (app/schemas/.../18.json), проходит валидацию схемой приложения
     * без миграций — то есть схема в git не расходится с тем, что генерирует
     * Room по entities (иначе — рассинхрон identity hash / структуры таблиц).
     */
    @Test
    fun `database current version built from exported schema validates cleanly`() {
        helper.createDatabase(18).close()
        helper.runMigrationsAndValidate(18, emptyList())
    }

    /**
     * 16 → 17: архив категорий. Колонка archived добавляется со значением 0 —
     * существующие списки остаются активными, данные не теряются.
     */
    @Test
    fun `migrate 16 to 17 adds archived column defaulting to zero`() {
        helper.createDatabase(16).use { db ->
            db.prepare(
                "INSERT INTO categories (id, name, color, position) VALUES (1, 'Продукты', -65536, 0)",
            ).use { it.step() }
        }
        val db = helper.runMigrationsAndValidate(
            17,
            listOf(AppDatabaseMigration.MIGRATION_16_17),
        )
        db.prepare("SELECT name, archived FROM categories WHERE id = 1").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Продукты", stmt.getText(0))
            assertEquals(0L, stmt.getLong(1))
        }
        db.close()
    }

    /**
     * 17 → 18: теги заметок. Колонка tags добавляется пустой — существующие
     * заметки не теряют ни байта данных, их теги вводит пользователь позже.
     */
    @Test
    fun `migrate 17 to 18 adds tags column defaulting to empty`() {
        helper.createDatabase(17).use { db ->
            db.prepare(
                "INSERT INTO notes (id, title, content, createdAt, position) " +
                    "VALUES (1, 'Борщ', '## рецепт', 1000, 0)",
            ).use { it.step() }
        }
        val db = helper.runMigrationsAndValidate(
            18,
            listOf(AppDatabaseMigration.MIGRATION_17_18),
        )
        db.prepare("SELECT title, content, tags FROM notes WHERE id = 1").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Борщ", stmt.getText(0))
            assertEquals("## рецепт", stmt.getText(1))
            assertEquals("", stmt.getText(2))
        }
        db.close()
    }

    /**
     * 18 → 19: CalDAV-uid событий. Каждой строке выдаётся свой UID; двух
     * одинаковых быть не может — они нужны дальше как ключ сопоставления
     * «наше событие ↔ серверный объект» при синхронизации.
     */
    @Test
    fun `migrate 18 to 19 assigns a distinct uid to every event`() {
        val db18 = helper.createDatabase(18)
        db18.use { db ->
            db.prepare(
                "INSERT INTO scheduled_events (id, eventDate, title, type, " +
                    "icon, colorArgb, repeatIntervalDays, repeatDays, hidePast, position) " +
                    "VALUES (1, '2026-02-17', 'Ремонт', 'REGULAR', 'NOTE', 0, NULL, 0, 0, 0)",
            ).use { it.step() }
            db.prepare(
                "INSERT INTO scheduled_events (id, eventDate, title, type, " +
                    "icon, colorArgb, repeatIntervalDays, repeatDays, hidePast, position) " +
                    "VALUES (2, '1988-08-09', 'ДР Маши', 'BIRTHDAY', 'NOTE', -65536, NULL, 0, 0, 1)",
            ).use { it.step() }
            db.prepare(
                "INSERT INTO calendar_notes (eventDate, text) VALUES ('2026-02-17', 'старт')",
            ).use { it.step() }
        }
        val db = helper.runMigrationsAndValidate(
            19,
            listOf(AppDatabaseMigration.MIGRATION_18_19),
        )
        db.prepare(
            "SELECT title, uid FROM scheduled_events ORDER BY id",
        ).use { stmt ->
            val rows = buildList {
                while (stmt.step()) add(stmt.getText(0) to stmt.getText(1))
            }
            // Данные не тронуты, а каждому событию выдан свой UID — ключ
            // синхронизации готов ещё до первого PUT.
            assertEquals(listOf("Ремонт", "ДР Маши"), rows.map { it.first })
            val uids = rows.map { it.second }
            assertEquals(uids.toSet().size, uids.size)
            uids.forEach { uid ->
                assertTrue(uid.length == 32 && uid.all { c -> c in '0'..'9' || c in 'a'..'f' })
            }
        }
        db.prepare("SELECT text FROM calendar_notes WHERE eventDate = '2026-02-17'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("старт", stmt.getText(0))
        }
        db.close()
    }

    /**
     * 19 → 20: CalDAV-подписка. Новая таблица аккаунтов + колонка-источник
     * scheduled_events.caldavAccountId: существующие строки остаются
     * локальными (NULL) — до подключения подписки ничего не меняется.
     */
    @Test
    fun `migrate 19 to 20 adds caldav accounts and origin column`() {
        helper.createDatabase(19).use { db ->
            db.prepare(
                "INSERT INTO scheduled_events (id, uid, eventDate, title, type, " +
                    "icon, colorArgb, repeatIntervalDays, repeatDays, hidePast, position) " +
                    "VALUES (1, 'uid-1', '2026-02-17', 'Ремонт', 'REGULAR', 'NOTE', 0, NULL, 0, 0, 0)",
            ).use { it.step() }
        }
        val db = helper.runMigrationsAndValidate(
            20,
            listOf(AppDatabaseMigration.MIGRATION_19_20),
        )
        // Событие пережило миграцию и осталось локальным (NULL-источник).
        db.prepare("SELECT title FROM scheduled_events WHERE caldavAccountId IS NULL").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("Ремонт", stmt.getText(0))
        }
        // Новая таблица принимает строку аккаунта.
        db.prepare(
            "INSERT INTO caldav_accounts (id, serverUrl, username, collectionUrl, " +
                "collectionName, lastSyncAt) VALUES (1, 'https://caldav.yandex.ru', " +
                "'taur-lom@yandex.ru', '/calendars/taur-lom%40yandex.ru/events-1/', 'T-Note', 0)",
        ).use { it.step() }
        db.prepare("SELECT collectionName FROM caldav_accounts WHERE id = 1").use { stmt ->
            assertTrue(stmt.step())
            assertEquals("T-Note", stmt.getText(0))
        }
        db.close()
    }

    /**
     * 20 → 21: повторения как в RRULE. Старые поля переводятся в новые:
     * интервал «раз в N дней» → частота DAYS с тем же интервалом, окно
     * «повторять дней» → дата-ограничитель repeatUntil (якорь + окно − 1).
     * Событие без повторения получает repeatInterval = 1 и пустые правила.
     */
    @Test
    fun `migrate 20 to 21 converts legacy repeat fields`() {
        helper.createDatabase(20).use { db ->
            // Повторяющееся: раз в 3 дня, окно 10 дней (до 2026-01-10).
            db.prepare(
                "INSERT INTO scheduled_events (id, uid, eventDate, title, type, " +
                    "icon, colorArgb, repeatIntervalDays, repeatDays, hidePast, position) " +
                    "VALUES (1, 'uid-1', '2026-01-01', 'Зарядка', 'REPEATING', 'NOTE', 0, 3, 10, 0, 0)",
            ).use { it.step() }
            // Обычное событие: правила нет вовсе (NULL-интервал).
            db.prepare(
                "INSERT INTO scheduled_events (id, uid, eventDate, title, type, " +
                    "icon, colorArgb, repeatIntervalDays, repeatDays, hidePast, position) " +
                    "VALUES (2, 'uid-2', '2026-02-17', 'Ремонт', 'REGULAR', 'NOTE', 0, NULL, 0, 0, 1)",
            ).use { it.step() }
        }
        val db = helper.runMigrationsAndValidate(
            21,
            listOf(AppDatabaseMigration.MIGRATION_20_21),
        )
        db.prepare(
            "SELECT title, repeatUnit, repeatInterval, repeatWeekdays, repeatUntil " +
                "FROM scheduled_events ORDER BY id",
        ).use { stmt ->
            // Повторяющееся: частота DAYS, интервал сохранён, окно стало датой.
            assertTrue(stmt.step())
            assertEquals("Зарядка", stmt.getText(0))
            assertEquals("DAYS", stmt.getText(1))
            assertEquals(3L, stmt.getLong(2))
            assertTrue(stmt.isNull(3))
            assertEquals("2026-01-10", stmt.getText(4))
            // Обычное: без частоты, интервал по умолчанию.
            assertTrue(stmt.step())
            assertEquals("Ремонт", stmt.getText(0))
            assertTrue(stmt.isNull(1))
            assertEquals(1L, stmt.getLong(2))
            assertTrue(stmt.isNull(4))
        }
        db.close()
    }

    /**
     * 21 → 22: отменённые вхождения (EXDATE) и метки времени. Колонки
     * добавляются пустыми: отмены копятся с сервера, моменты времени —
     * с момента первой правки в новой версии.
     */
    @Test
    fun `migrate 21 to 22 adds exdates and timestamps`() {
        helper.createDatabase(21).use { db ->
            // Повтор по понедельникам без отмен и меток.
            db.prepare(
                "INSERT INTO scheduled_events (id, uid, eventDate, title, type, " +
                    "icon, colorArgb, repeatUnit, repeatInterval, repeatWeekdays, " +
                    "repeatUntil, hidePast, position) " +
                    "VALUES (1, 'uid-1', '2026-11-16', 'Полив', 'REPEATING', 'NOTE', 0, " +
                    "'WEEKS', 1, 'MO', NULL, 0, 0)",
            ).use { it.step() }
            // Заметка дня без меток времени.
            db.prepare(
                "INSERT INTO calendar_notes (eventDate, text) VALUES ('2026-11-16', 'Первая заметка')",
            ).use { it.step() }
        }
        val db = helper.runMigrationsAndValidate(
            22,
            listOf(AppDatabaseMigration.MIGRATION_21_22),
        )
        db.prepare("SELECT exdates, createdAt, modifiedAt FROM scheduled_events WHERE id = 1").use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0))
            assertTrue(stmt.isNull(1))
            assertTrue(stmt.isNull(2))
        }
        db.prepare("SELECT createdAt, modifiedAt FROM calendar_notes WHERE eventDate = '2026-11-16'").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(0L, stmt.getLong(0))
            assertEquals(0L, stmt.getLong(1))
        }
        db.close()
    }
}
