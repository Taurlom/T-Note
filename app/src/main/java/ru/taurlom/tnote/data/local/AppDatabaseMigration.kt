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
     * Теги заметок: колонка notes.tags. Аддитивная миграция — существующие
     * заметки получают пустой тег-лист, данные не затрагиваются. DEFAULT ''
     * совпадает с @ColumnInfo(defaultValue = "") у NoteEntity, иначе
     * валидация схемы Room после миграции упадёт.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    /**
     * CalDAV: колонка scheduled_events.uid + уникальный индекс. Столбец
     * добавляется NOT NULL DEFAULT '' (пустая строка — временный маркер,
     * валидация Room совпадает с @ColumnInfo(defaultValue = "")), после чего
     * каждая существующая строка получает свой UUID: randomblob пересчитывается
     * для каждой строки UPDATE по отдельности, коллизий между ними нет.
     * Дубликатов uid в данных нет и дальше — уникальный индекс их исключает.
     */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE scheduled_events ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
            )
            db.execSQL(
                "UPDATE scheduled_events SET uid = lower(hex(randomblob(16)))",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX index_scheduled_events_uid " +
                    "ON scheduled_events(uid)",
            )
        }
    }

    /**
     * CalDAV-аккаунты: таблица подключённых календарей + колонка
     * scheduled_events.caldavAccountId (источник события; null — локальное).
     * Колонка добавляется без DEFAULT — она nullable, валидация Room
     * требует именно отсутствие значения по умолчанию.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS caldav_accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "serverUrl TEXT NOT NULL, " +
                    "username TEXT NOT NULL, " +
                    "collectionUrl TEXT NOT NULL, " +
                    "collectionName TEXT NOT NULL, " +
                    "lastSyncAt INTEGER NOT NULL, " +
                    "lastError TEXT)",
            )
            db.execSQL(
                "ALTER TABLE scheduled_events ADD COLUMN caldavAccountId INTEGER",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_caldavAccountId " +
                    "ON scheduled_events(caldavAccountId)",
            )
        }
    }

    /**
     * Архив категорий: флаг archived. Аддитивная миграция — существующие
     * списки остаются активными (0). DEFAULT 0 совпадает с
     * @ColumnInfo(defaultValue = "0") у CategoryEntity, иначе валидация
     * схемы Room после миграции упадёт.
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE categories " +
                    "ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

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

    /**
     * Повторения как в RRULE (RFC 5545) с интерфейсом как в Яндексе:
     * частота DAYS/WEEKS/MONTHS/YEARS, интервал «каждые N», дни недели
     * для недельных и дата-ограничитель «повторять до» вместо окна в днях.
     * Старые поля переводятся: repeatIntervalDays → частота DAYS с тем же
     * интервалом, окно repeatDays → repeatUntil (якорь + окно − 1 день).
     * Пересоздание таблицы вместо DROP COLUMN: на Android < 13 SQLite его
     * не поддерживает.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS scheduled_events_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uid TEXT NOT NULL DEFAULT '',
                    eventDate TEXT NOT NULL,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT 'NOTE',
                    colorArgb INTEGER NOT NULL DEFAULT 0,
                    repeatUnit TEXT,
                    repeatInterval INTEGER NOT NULL DEFAULT 1,
                    repeatWeekdays TEXT,
                    repeatUntil TEXT,
                    hidePast INTEGER NOT NULL DEFAULT 0,
                    position INTEGER NOT NULL,
                    caldavAccountId INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO scheduled_events_new (id, uid, eventDate, title, type, icon, colorArgb, " +
                    "repeatUnit, repeatInterval, repeatWeekdays, repeatUntil, hidePast, position, caldavAccountId) " +
                    "SELECT id, uid, eventDate, title, type, icon, colorArgb, " +
                    "CASE WHEN type = 'REPEATING' THEN 'DAYS' END, " +
                    "COALESCE(repeatIntervalDays, 1), " +
                    "NULL, " +
                    "CASE WHEN type = 'REPEATING' AND repeatDays > 0 " +
                    "THEN date(eventDate, '+' || CAST(repeatDays - 1 AS TEXT) || ' days') END, " +
                    "hidePast, position, caldavAccountId FROM scheduled_events",
            )
            db.execSQL("DROP TABLE scheduled_events")
            db.execSQL("ALTER TABLE scheduled_events_new RENAME TO scheduled_events")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_eventDate " +
                    "ON scheduled_events(eventDate)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_scheduled_events_uid " +
                    "ON scheduled_events(uid)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_scheduled_events_caldavAccountId " +
                    "ON scheduled_events(caldavAccountId)",
            )
        }
    }

    /**
     * 21 → 22: отменённые вхождения (EXDATE) и метки времени для будущего
     * разрешения конфликтов CalDAV-этапа 2 («последняя правка побеждает»).
     * Событию — nullable-колонки (null — момент неизвестен), заметке дня —
     * 0-умолчания: заметки вне синхронизации, там важнее не сломать старые
     * бэкапы, а факт «время неизвестно» различать не нужно.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN exdates TEXT")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN createdAt INTEGER")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN modifiedAt INTEGER")
            db.execSQL("ALTER TABLE calendar_notes ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE calendar_notes ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 22 → 23: очередь двустороннего обмена CalDAV (этап 2). Событиям
     * добавляются: флаг dirty («локальная правка ждёт PUT»), etag (предусловие
     * If-Match) и serverHref (адрес объекта — не выводить имя файла из UID).
     * Удаления гуляют через таблицу caldav_tombstones: без неё pull вернул бы
     * удалённое пользователем событие при первой же вычитке. Все колонки
     * пустые/нулевые — до первой правки событию нечего отправлять.
     */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN etag TEXT")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN serverHref TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS caldav_tombstones (
                    uid TEXT NOT NULL PRIMARY KEY,
                    accountId INTEGER NOT NULL,
                    href TEXT,
                    etag TEXT,
                    deletedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_caldav_tombstones_accountId " +
                    "ON caldav_tombstones(accountId)",
            )
        }
    }

    /**
     * 26 → 27: время события. Событию добавляются три nullable-колонки:
     * startMinutes и endMinutes (минуты от полуночи) и endDate (дата конца
     * события, "yyyy-MM-dd"). Существующие события остаются дневными
     * метками — времени у них нет (NULL), данные не затрагиваются. Значения
     * по умолчанию не нужны: колонки nullable, валидация Room требует именно
     * отсутствие DEFAULT.
     *
     * endDate — конец события длиннее суток (у чужих: Google, Яндекс):
     * «1 марта 10:00 — 5 марта 18:00» хранится одной строкой, а в календаре
     * разворачивается по дням (см. ScheduledEvent.windowOn).
     */
    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN startMinutes INTEGER")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN endMinutes INTEGER")
            db.execSQL("ALTER TABLE scheduled_events ADD COLUMN endDate TEXT")
        }
    }

    /**
     * 25 → 26: провайдер синхронизации в caldav_accounts — YANDEX (CalDAV)
     * или GOOGLE (Calendar API v3, этап 3). Аддитивная миграция: все
     * существующие аккаунты — Яндекс, других до этапа 3 не было.
     */
    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE caldav_accounts ADD COLUMN provider TEXT NOT NULL DEFAULT 'YANDEX'",
            )
        }
    }

    /**
     * 24 → 25: заметки дня едут через Яндекса событиями-носителями —
     * обвязка двустороннего обмена (приписка подписке, очередь PUT,
     * ETag/href от вычитки). Заодно в очередь встают и старые события без
     * приписки (включая «выходные», которые отныне тоже едут): у них нет
     * серверного следа, и перенос на другое устройство не должен требовать
     * правки каждой строки. Приписка — только при единственной подписке;
     * заметки без подписки остаются локальными.
     */
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE calendar_notes ADD COLUMN caldavAccountId INTEGER")
            db.execSQL("ALTER TABLE calendar_notes ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE calendar_notes ADD COLUMN etag TEXT")
            db.execSQL("ALTER TABLE calendar_notes ADD COLUMN serverHref TEXT")
            db.execSQL(
                "UPDATE calendar_notes SET caldavAccountId = (SELECT id FROM caldav_accounts), " +
                    "dirty = 1 WHERE (SELECT COUNT(*) FROM caldav_accounts) = 1",
            )
            db.execSQL(
                "UPDATE scheduled_events SET caldavAccountId = (SELECT id FROM caldav_accounts), " +
                    "dirty = 1 WHERE caldavAccountId IS NULL AND (SELECT COUNT(*) FROM caldav_accounts) = 1",
            )
        }
    }

    /**
     * 23 → 24: флаг происхождения «крайняя версия содержимого приехала
     * вычиткой» (remoteImported). Различает импорт и собственные события,
     * отправленные в подписанный календарь: приписка подписке
     * (caldavAccountId) бывает у тех и других, а бейдж «Яндекс» раньше
     * смотрел только на неё — маркировались и события, созданные в
     * приложении. Старым строкам остаётся 0: история происхождения не
     * восстановима, отсутствие бейджа у таких событий молчаливо.
     */
    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE scheduled_events ADD COLUMN remoteImported INTEGER NOT NULL DEFAULT 0",
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
