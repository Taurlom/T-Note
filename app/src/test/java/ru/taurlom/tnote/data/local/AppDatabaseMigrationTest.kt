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
 * Шаблон для будущей миграции 17 -> 18:
 * ```
 * helper.createDatabase(17).apply { /* наполнить старыми данными */ close() }
 * helper.runMigrationsAndValidate(18, listOf(AppDatabaseMigration.MIGRATION_17_18))
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
     * схеме (app/schemas/.../17.json), проходит валидацию схемой приложения
     * без миграций — то есть схема в git не расходится с тем, что генерирует
     * Room по entities (иначе — рассинхрон identity hash / структуры таблиц).
     */
    @Test
    fun `database current version built from exported schema validates cleanly`() {
        helper.createDatabase(17).close()
        helper.runMigrationsAndValidate(17, emptyList())
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
}
