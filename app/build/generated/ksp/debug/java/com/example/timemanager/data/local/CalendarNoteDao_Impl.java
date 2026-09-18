package com.example.timemanager.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.timemanager.data.local.entity.CalendarNoteEntity;
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
public final class CalendarNoteDao_Impl implements CalendarNoteDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CalendarNoteEntity> __insertionAdapterOfCalendarNoteEntity;

  private final EntityDeletionOrUpdateAdapter<CalendarNoteEntity> __deletionAdapterOfCalendarNoteEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByDate;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public CalendarNoteDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCalendarNoteEntity = new EntityInsertionAdapter<CalendarNoteEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `calendar_notes` (`id`,`eventDate`,`text`) VALUES (nullif(?, 0),?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CalendarNoteEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getEventDate());
        statement.bindString(3, entity.getText());
      }
    };
    this.__deletionAdapterOfCalendarNoteEntity = new EntityDeletionOrUpdateAdapter<CalendarNoteEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `calendar_notes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CalendarNoteEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteByDate = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM calendar_notes WHERE eventDate = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM calendar_notes";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final CalendarNoteEntity note,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCalendarNoteEntity.insertAndReturnId(note);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final CalendarNoteEntity note,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCalendarNoteEntity.handle(note);
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
  public Flow<List<CalendarNoteEntity>> getByDate(final String date) {
    final String _sql = "SELECT * FROM calendar_notes WHERE eventDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, date);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"calendar_notes"}, new Callable<List<CalendarNoteEntity>>() {
      @Override
      @NonNull
      public List<CalendarNoteEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEventDate = CursorUtil.getColumnIndexOrThrow(_cursor, "eventDate");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final List<CalendarNoteEntity> _result = new ArrayList<CalendarNoteEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CalendarNoteEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpEventDate;
            _tmpEventDate = _cursor.getString(_cursorIndexOfEventDate);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            _item = new CalendarNoteEntity(_tmpId,_tmpEventDate,_tmpText);
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
  public Object getByDateOnce(final String date,
      final Continuation<? super CalendarNoteEntity> $completion) {
    final String _sql = "SELECT * FROM calendar_notes WHERE eventDate = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, date);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CalendarNoteEntity>() {
      @Override
      @Nullable
      public CalendarNoteEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEventDate = CursorUtil.getColumnIndexOrThrow(_cursor, "eventDate");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final CalendarNoteEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpEventDate;
            _tmpEventDate = _cursor.getString(_cursorIndexOfEventDate);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            _result = new CalendarNoteEntity(_tmpId,_tmpEventDate,_tmpText);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CalendarNoteEntity>> getByMonthPrefix(final String monthPrefix) {
    final String _sql = "SELECT * FROM calendar_notes WHERE eventDate LIKE ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, monthPrefix);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"calendar_notes"}, new Callable<List<CalendarNoteEntity>>() {
      @Override
      @NonNull
      public List<CalendarNoteEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfEventDate = CursorUtil.getColumnIndexOrThrow(_cursor, "eventDate");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final List<CalendarNoteEntity> _result = new ArrayList<CalendarNoteEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CalendarNoteEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpEventDate;
            _tmpEventDate = _cursor.getString(_cursorIndexOfEventDate);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            _item = new CalendarNoteEntity(_tmpId,_tmpEventDate,_tmpText);
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
