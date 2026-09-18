package com.example.timemanager.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.timemanager.data.local.entity.CalendarTaskEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CalendarTaskDao_Impl implements CalendarTaskDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CalendarTaskEntity> __insertionAdapterOfCalendarTaskEntity;

  private final EntityDeletionOrUpdateAdapter<CalendarTaskEntity> __deletionAdapterOfCalendarTaskEntity;

  private final EntityDeletionOrUpdateAdapter<CalendarTaskEntity> __updateAdapterOfCalendarTaskEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByDate;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public CalendarTaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCalendarTaskEntity = new EntityInsertionAdapter<CalendarTaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `calendar_tasks` (`id`,`eventDate`,`text`,`isCompleted`,`position`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CalendarTaskEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getEventDate());
        statement.bindString(3, entity.getText());
        final int _tmp = entity.isCompleted() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getPosition());
      }
    };
    this.__deletionAdapterOfCalendarTaskEntity = new EntityDeletionOrUpdateAdapter<CalendarTaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `calendar_tasks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CalendarTaskEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfCalendarTaskEntity = new EntityDeletionOrUpdateAdapter<CalendarTaskEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `calendar_tasks` SET `id` = ?,`eventDate` = ?,`text` = ?,`isCompleted` = ?,`position` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CalendarTaskEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getEventDate());
        statement.bindString(3, entity.getText());
        final int _tmp = entity.isCompleted() ? 1 : 0;
        statement.bindLong(4, _tmp);
        statement.bindLong(5, entity.getPosition());
        statement.bindLong(6, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteByDate = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM calendar_tasks WHERE eventDate = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM calendar_tasks";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final CalendarTaskEntity task,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCalendarTaskEntity.insertAndReturnId(task);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final CalendarTaskEntity task,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCalendarTaskEntity.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final CalendarTaskEntity task,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCalendarTaskEntity.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByDate(final String date, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByDate.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, date);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByDate.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CalendarTaskEntity>> getByDate(final String date) {
    final String _sql = "SELECT * FROM calendar_tasks WHERE eventDate = ? ORDER BY position ASC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, date);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"calendar_tasks"}, new Callable<List<CalendarTaskEntity>>() {
      @Override
      @NonNull
      public List<CalendarTaskEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEventDate = CursorUtil.getColumnIndexOrThrow(_cursor, "eventDate");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "position");
          final List<CalendarTaskEntity> _result = new ArrayList<CalendarTaskEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CalendarTaskEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpEventDate;
            _tmpEventDate = _cursor.getString(_cursorIndexOfEventDate);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final boolean _tmpIsCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp != 0;
            final int _tmpPosition;
            _tmpPosition = _cursor.getInt(_cursorIndexOfPosition);
            _item = new CalendarTaskEntity(_tmpId,_tmpEventDate,_tmpText,_tmpIsCompleted,_tmpPosition);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<CalendarTaskEntity>> getByMonthPrefix(final String monthPrefix) {
    final String _sql = "SELECT * FROM calendar_tasks WHERE eventDate LIKE ? ORDER BY position ASC, id ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, monthPrefix);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"calendar_tasks"}, new Callable<List<CalendarTaskEntity>>() {
      @Override
      @NonNull
      public List<CalendarTaskEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEventDate = CursorUtil.getColumnIndexOrThrow(_cursor, "eventDate");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "position");
          final List<CalendarTaskEntity> _result = new ArrayList<CalendarTaskEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CalendarTaskEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpEventDate;
            _tmpEventDate = _cursor.getString(_cursorIndexOfEventDate);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final boolean _tmpIsCompleted;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp != 0;
            final int _tmpPosition;
            _tmpPosition = _cursor.getInt(_cursorIndexOfPosition);
            _item = new CalendarTaskEntity(_tmpId,_tmpEventDate,_tmpText,_tmpIsCompleted,_tmpPosition);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
