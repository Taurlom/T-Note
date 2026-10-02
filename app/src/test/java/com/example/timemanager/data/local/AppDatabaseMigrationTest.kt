package com.example.timemanager.data.local

import android.content.Context
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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
 * Шаблон для будущей миграции 16 -> 17:
 * ```
 * helper.createDatabase(16).apply { /* наполнить старыми данными */ close() }
 * helper.runMigrationsAndValidate(17, listOf(AppDatabaseMigration.MIGRATION_16_17))
 * ```
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val dbFile = File(
        ApplicationProvider.getApplicationContext<Context>().cacheDir,
        "migration-test.db"
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
        autoMigrationSpecs = emptyList<AutoMigrationSpec>()
    )

    /**
     * Базовая проверка: база версии 16, созданная по экспортированной схеме
     * (app/schemas/.../16.json), проходит валидацию текущей версией приложения
     * без миграций — то есть схема в git не расходится с тем, что генерирует
     * Room по entities (иначе — рассинхрон identity hash / структуры таблиц).
     */
    @Test
    fun `database v16 built from exported schema validates cleanly`() {
        helper.createDatabase(16).close()
        helper.runMigrationsAndValidate(16, emptyList())
    }
}
