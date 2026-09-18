package com.example.timemanager.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile CategoryDao _categoryDao;

  private volatile TaskDao _taskDao;

  private volatile CalendarNoteDao _calendarNoteDao;

  private volatile CalendarTaskDao _calendarTaskDao;

  private volatile DocumentDao _documentDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(6) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `position` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventDate` TEXT NOT NULL, `text` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventDate` TEXT NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `position` INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_tasks_eventDate` ON `calendar_tasks` (`eventDate`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `document_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentId` INTEGER NOT NULL, `photoPath` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_photos_documentId` ON `document_photos` (`documentId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '39171f799e72645c6d117eb666af1e4d')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `categories`");
        db.execSQL("DROP TABLE IF EXISTS `tasks`");
        db.execSQL("DROP TABLE IF EXISTS `calendar_notes`");
        db.execSQL("DROP TABLE IF EXISTS `calendar_tasks`");
        db.execSQL("DROP TABLE IF EXISTS `documents`");
        db.execSQL("DROP TABLE IF EXISTS `document_photos`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsCategories = new HashMap<String, TableInfo.Column>(4);
        _columnsCategories.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("color", new TableInfo.Column("color", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCategories.put("position", new TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCategories = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCategories = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCategories = new TableInfo("categories", _columnsCategories, _foreignKeysCategories, _indicesCategories);
        final TableInfo _existingCategories = TableInfo.read(db, "categories");
        if (!_infoCategories.equals(_existingCategories)) {
          return new RoomOpenHelper.ValidationResult(false, "categories(com.example.timemanager.data.local.entity.CategoryEntity).\n"
                  + " Expected:\n" + _infoCategories + "\n"
                  + " Found:\n" + _existingCategories);
        }
        final HashMap<String, TableInfo.Column> _columnsTasks = new HashMap<String, TableInfo.Column>(7);
        _columnsTasks.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("isCompleted", new TableInfo.Column("isCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("categoryId", new TableInfo.Column("categoryId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTasks.put("position", new TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTasks = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysTasks.add(new TableInfo.ForeignKey("categories", "CASCADE", "NO ACTION", Arrays.asList("categoryId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesTasks = new HashSet<TableInfo.Index>(1);
        _indicesTasks.add(new TableInfo.Index("index_tasks_categoryId", false, Arrays.asList("categoryId"), Arrays.asList("ASC")));
        final TableInfo _infoTasks = new TableInfo("tasks", _columnsTasks, _foreignKeysTasks, _indicesTasks);
        final TableInfo _existingTasks = TableInfo.read(db, "tasks");
        if (!_infoTasks.equals(_existingTasks)) {
          return new RoomOpenHelper.ValidationResult(false, "tasks(com.example.timemanager.data.local.entity.TaskEntity).\n"
                  + " Expected:\n" + _infoTasks + "\n"
                  + " Found:\n" + _existingTasks);
        }
        final HashMap<String, TableInfo.Column> _columnsCalendarNotes = new HashMap<String, TableInfo.Column>(3);
        _columnsCalendarNotes.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCalendarNotes.put("eventDate", new TableInfo.Column("eventDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCalendarNotes.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCalendarNotes = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCalendarNotes = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCalendarNotes = new TableInfo("calendar_notes", _columnsCalendarNotes, _foreignKeysCalendarNotes, _indicesCalendarNotes);
        final TableInfo _existingCalendarNotes = TableInfo.read(db, "calendar_notes");
        if (!_infoCalendarNotes.equals(_existingCalendarNotes)) {
          return new RoomOpenHelper.ValidationResult(false, "calendar_notes(com.example.timemanager.data.local.entity.CalendarNoteEntity).\n"
                  + " Expected:\n" + _infoCalendarNotes + "\n"
                  + " Found:\n" + _existingCalendarNotes);
        }
        final HashMap<String, TableInfo.Column> _columnsCalendarTasks = new HashMap<String, TableInfo.Column>(5);
        _columnsCalendarTasks.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCalendarTasks.put("eventDate", new TableInfo.Column("eventDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCalendarTasks.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCalendarTasks.put("isCompleted", new TableInfo.Column("isCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCalendarTasks.put("position", new TableInfo.Column("position", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCalendarTasks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCalendarTasks = new HashSet<TableInfo.Index>(1);
        _indicesCalendarTasks.add(new TableInfo.Index("index_calendar_tasks_eventDate", false, Arrays.asList("eventDate"), Arrays.asList("ASC")));
        final TableInfo _infoCalendarTasks = new TableInfo("calendar_tasks", _columnsCalendarTasks, _foreignKeysCalendarTasks, _indicesCalendarTasks);
        final TableInfo _existingCalendarTasks = TableInfo.read(db, "calendar_tasks");
        if (!_infoCalendarTasks.equals(_existingCalendarTasks)) {
          return new RoomOpenHelper.ValidationResult(false, "calendar_tasks(com.example.timemanager.data.local.entity.CalendarTaskEntity).\n"
                  + " Expected:\n" + _infoCalendarTasks + "\n"
                  + " Found:\n" + _existingCalendarTasks);
        }
        final HashMap<String, TableInfo.Column> _columnsDocuments = new HashMap<String, TableInfo.Column>(4);
        _columnsDocuments.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocuments.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDocuments = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDocuments = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDocuments = new TableInfo("documents", _columnsDocuments, _foreignKeysDocuments, _indicesDocuments);
        final TableInfo _existingDocuments = TableInfo.read(db, "documents");
        if (!_infoDocuments.equals(_existingDocuments)) {
          return new RoomOpenHelper.ValidationResult(false, "documents(com.example.timemanager.data.local.entity.DocumentEntity).\n"
                  + " Expected:\n" + _infoDocuments + "\n"
                  + " Found:\n" + _existingDocuments);
        }
        final HashMap<String, TableInfo.Column> _columnsDocumentPhotos = new HashMap<String, TableInfo.Column>(4);
        _columnsDocumentPhotos.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocumentPhotos.put("documentId", new TableInfo.Column("documentId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocumentPhotos.put("photoPath", new TableInfo.Column("photoPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDocumentPhotos.put("orderIndex", new TableInfo.Column("orderIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDocumentPhotos = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysDocumentPhotos.add(new TableInfo.ForeignKey("documents", "CASCADE", "NO ACTION", Arrays.asList("documentId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesDocumentPhotos = new HashSet<TableInfo.Index>(1);
        _indicesDocumentPhotos.add(new TableInfo.Index("index_document_photos_documentId", false, Arrays.asList("documentId"), Arrays.asList("ASC")));
        final TableInfo _infoDocumentPhotos = new TableInfo("document_photos", _columnsDocumentPhotos, _foreignKeysDocumentPhotos, _indicesDocumentPhotos);
        final TableInfo _existingDocumentPhotos = TableInfo.read(db, "document_photos");
        if (!_infoDocumentPhotos.equals(_existingDocumentPhotos)) {
          return new RoomOpenHelper.ValidationResult(false, "document_photos(com.example.timemanager.data.local.entity.DocumentPhotoEntity).\n"
                  + " Expected:\n" + _infoDocumentPhotos + "\n"
                  + " Found:\n" + _existingDocumentPhotos);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "39171f799e72645c6d117eb666af1e4d", "635bb2ddfebd1d6861c3ed0118088aee");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "categories","tasks","calendar_notes","calendar_tasks","documents","document_photos");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `categories`");
      _db.execSQL("DELETE FROM `tasks`");
      _db.execSQL("DELETE FROM `calendar_notes`");
      _db.execSQL("DELETE FROM `calendar_tasks`");
      _db.execSQL("DELETE FROM `documents`");
      _db.execSQL("DELETE FROM `document_photos`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(CategoryDao.class, CategoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TaskDao.class, TaskDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CalendarNoteDao.class, CalendarNoteDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CalendarTaskDao.class, CalendarTaskDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DocumentDao.class, DocumentDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public CategoryDao categoryDao() {
    if (_categoryDao != null) {
      return _categoryDao;
    } else {
      synchronized(this) {
        if(_categoryDao == null) {
          _categoryDao = new CategoryDao_Impl(this);
        }
        return _categoryDao;
      }
    }
  }

  @Override
  public TaskDao taskDao() {
    if (_taskDao != null) {
      return _taskDao;
    } else {
      synchronized(this) {
        if(_taskDao == null) {
          _taskDao = new TaskDao_Impl(this);
        }
        return _taskDao;
      }
    }
  }

  @Override
  public CalendarNoteDao calendarNoteDao() {
    if (_calendarNoteDao != null) {
      return _calendarNoteDao;
    } else {
      synchronized(this) {
        if(_calendarNoteDao == null) {
          _calendarNoteDao = new CalendarNoteDao_Impl(this);
        }
        return _calendarNoteDao;
      }
    }
  }

  @Override
  public CalendarTaskDao calendarTaskDao() {
    if (_calendarTaskDao != null) {
      return _calendarTaskDao;
    } else {
      synchronized(this) {
        if(_calendarTaskDao == null) {
          _calendarTaskDao = new CalendarTaskDao_Impl(this);
        }
        return _calendarTaskDao;
      }
    }
  }

  @Override
  public DocumentDao documentDao() {
    if (_documentDao != null) {
      return _documentDao;
    } else {
      synchronized(this) {
        if(_documentDao == null) {
          _documentDao = new DocumentDao_Impl(this);
        }
        return _documentDao;
      }
    }
  }
}
