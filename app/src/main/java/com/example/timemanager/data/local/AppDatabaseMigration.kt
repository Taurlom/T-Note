package com.example.timemanager.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigration {

    /**
     * Убирает из scheduled_events колонку repeatPeriod (поле «Повторять» с
     * выбором периода удалён). Существующие периоды переводятся в свой
     * интервал «раз в N дней», чтобы частота повторов сохранилась.
     * Пересоздание таблицы вместо DROP COLUMN: на Android < 13 SQLite его не поддерживает.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE scheduled_events SET repeatIntervalDays = CASE repeatPeriod " +
                    "WHEN 'DAILY' THEN 1 WHEN 'WEEKLY' THEN 7 WHEN 'MONTHLY' THEN 30 END " +
                    "WHERE repeatPeriod IS NOT NULL AND repeatIntervalDays IS NULL"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scheduled_events_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    eventDate TEXT NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT 'NOTE',
                    colorArgb INTEGER NOT NULL DEFAULT 0,
                    repeatIntervalDays INTEGER,
                    repeatDays INTEGER NOT NULL DEFAULT 0,
                    hidePast INTEGER NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO scheduled_events_new (id, eventDate, title, type, icon, colorArgb, " +
                    "repeatIntervalDays, repeatDays, hidePast, position) " +
                    "SELECT id, eventDate, title, type, icon, colorArgb, repeatIntervalDays, " +
                    "repeatDays, hidePast, position FROM scheduled_events"
            )
            db.execSQL("DROP TABLE scheduled_events")
            db.execSQL("ALTER TABLE scheduled_events_new RENAME TO scheduled_events")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)"
            )
        }
    }

    /** Добавляет в scheduled_events настройки повторения. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN repeatPeriod TEXT")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN repeatIntervalDays INTEGER")
            db.execSQL(
                "ALTER TABLE scheduled_events " +
                    "ADD COLUMN repeatDays INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE scheduled_events " +
                    "ADD COLUMN hidePast INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /** Добавляет в documents поле порядка для перетаскивания в списке. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE documents " +
                    "ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /** Добавляет в scheduled_events цвет иконки события (0 — тематический). */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE scheduled_events " +
                    "ADD COLUMN colorArgb INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * Убирает из scheduled_events колонки времени и будильника (функциональность
     * удалена) и добавляет колонку иконки события. Пересоздание таблицы вместо
     * DROP COLUMN: на Android < 13 SQLite его не поддерживает.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scheduled_events_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    eventDate TEXT NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT 'NOTE',
                    position INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO scheduled_events_new (id, eventDate, title, type, icon, position) " +
                    "SELECT id, eventDate, title, type, 'NOTE', position FROM scheduled_events"
            )
            db.execSQL("DROP TABLE scheduled_events")
            db.execSQL("ALTER TABLE scheduled_events_new RENAME TO scheduled_events")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)"
            )
        }
    }

    /**
     * Удаляет таблицу calendar_tasks — незавершённую функциональность
     * «задачи календаря», которая никогда не была доступна в UI.
     * Остальные таблицы (заметки, события, списки, документы) не затрагиваются.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_calendar_tasks_eventDate")
            db.execSQL("DROP TABLE IF EXISTS calendar_tasks")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scheduled_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    eventDate TEXT NOT NULL,
                    title TEXT NOT NULL,
                    time TEXT,
                    type TEXT NOT NULL,
                    alarmEnabled INTEGER NOT NULL,
                    position INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)"
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = OFF")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS document_photos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    documentId INTEGER NOT NULL,
                    photoPath TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_document_photos_documentId ON document_photos(documentId)"
            )

            db.execSQL(
                """
                INSERT INTO document_photos (documentId, photoPath, orderIndex)
                SELECT id, photoPath, 0 FROM documents WHERE photoPath IS NOT NULL
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE documents_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO documents_new (id, title, description, createdAt)
                SELECT id, title, description, createdAt FROM documents
                """.trimIndent()
            )
            db.execSQL("DROP TABLE documents")
            db.execSQL("ALTER TABLE documents_new RENAME TO documents")

            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}
