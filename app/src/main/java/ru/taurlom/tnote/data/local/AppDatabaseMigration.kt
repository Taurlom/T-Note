package ru.taurlom.tnote.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigration {

    /**
     * Минимальная версия схемы, из которой существует цепочка миграций
     * (первая — [MIGRATION_5_6]). Восстановление резервных копий с более
     * старой схемой невозможно: Room открыл бы такую базу только с
     * destructive fallback, которого здесь нет намеренно.
     */
    const val MIN_SUPPORTED_VERSION = 5

    /**
     * Фото заметок: таблица note_photos по образцу document_photos.
     * Аддитивная миграция — существующие данные не затрагиваются.
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_photos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    noteId INTEGER NOT NULL,
                    photoPath TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL,
                    FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_note_photos_noteId " +
                    "ON note_photos(noteId)",
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Новый раздел «Заметки»: таблица добавляется, существующие данные
            // не затрагиваются вовсе. DDL повторяет генерируемый Room для
            // чистой установки (колонки, порядок, DEFAULT у position).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * Заметки календаря: eventDate становится первичным ключом вместо
     * авто-increment id. До этого каждое сохранение добавляло копию строки
     * (показывалась последняя по id, но дубли росли бесконечно). При миграции
     * на день оставляем последнюю по id заметку — ровно то, что и так
     * показывал интерфейс.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS calendar_notes_new (
                    eventDate TEXT NOT NULL PRIMARY KEY,
                    text TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO calendar_notes_new (eventDate, text) " +
                    "SELECT eventDate, text FROM calendar_notes " +
                    "WHERE id IN (SELECT MAX(id) FROM calendar_notes GROUP BY eventDate)",
            )
            db.execSQL("DROP TABLE calendar_notes")
            db.execSQL("ALTER TABLE calendar_notes_new RENAME TO calendar_notes")
        }
    }

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
                    "WHERE repeatPeriod IS NOT NULL AND repeatIntervalDays IS NULL",
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
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO scheduled_events_new (id, eventDate, title, type, icon, colorArgb, " +
                    "repeatIntervalDays, repeatDays, hidePast, position) " +
                    "SELECT id, eventDate, title, type, icon, colorArgb, repeatIntervalDays, " +
                    "repeatDays, hidePast, position FROM scheduled_events",
            )
            db.execSQL("DROP TABLE scheduled_events")
            db.execSQL("ALTER TABLE scheduled_events_new RENAME TO scheduled_events")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)",
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
                    "ADD COLUMN repeatDays INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE scheduled_events " +
                    "ADD COLUMN hidePast INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /** Добавляет в documents поле порядка для перетаскивания в списке. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE documents " +
                    "ADD COLUMN position INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /** Добавляет в scheduled_events цвет иконки события (0 — тематический). */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE scheduled_events " +
                    "ADD COLUMN colorArgb INTEGER NOT NULL DEFAULT 0",
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
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO scheduled_events_new (id, eventDate, title, type, icon, position) " +
                    "SELECT id, eventDate, title, type, 'NOTE', position FROM scheduled_events",
            )
            db.execSQL("DROP TABLE scheduled_events")
            db.execSQL("ALTER TABLE scheduled_events_new RENAME TO scheduled_events")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)",
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
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)",
            )
        }
    }

    /**
     * Документы: фото переезжают из колонки documents.photoPath в таблицу
     * document_photos. Порядок операций важен: Room выполняет миграцию внутри
     * транзакции, а PRAGMA foreign_keys внутри транзакции — no-op. Поэтому
     * document_photos создаётся ПОСЛЕ DROP старых documents: каскад от
     * пересоздаваемого родителя не задевает детскую таблицу, ссылки на фото
     * на время переезда лежат в отдельной таблице-кладе.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE photo_links_stash (
                    documentId INTEGER NOT NULL,
                    photoPath TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO photo_links_stash (documentId, photoPath) " +
                    "SELECT id, photoPath FROM documents WHERE photoPath IS NOT NULL",
            )

            db.execSQL(
                """
                CREATE TABLE documents_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO documents_new (id, title, description, createdAt)
                SELECT id, title, description, createdAt FROM documents
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE documents")
            db.execSQL("ALTER TABLE documents_new RENAME TO documents")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS document_photos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    documentId INTEGER NOT NULL,
                    photoPath TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL,
                    FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_document_photos_documentId ON document_photos(documentId)",
            )
            db.execSQL(
                "INSERT INTO document_photos (documentId, photoPath, orderIndex) " +
                    "SELECT documentId, photoPath, 0 FROM photo_links_stash",
            )
            db.execSQL("DROP TABLE photo_links_stash")
        }
    }
}
