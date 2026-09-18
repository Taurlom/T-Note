package com.example.timemanager.data.local;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.RelationUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.timemanager.data.local.entity.DocumentEntity;
import com.example.timemanager.data.local.entity.DocumentPhotoEntity;
import com.example.timemanager.data.local.entity.DocumentWithPhotos;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
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
public final class DocumentDao_Impl implements DocumentDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DocumentEntity> __insertionAdapterOfDocumentEntity;

  private final EntityInsertionAdapter<DocumentPhotoEntity> __insertionAdapterOfDocumentPhotoEntity;

  private final EntityDeletionOrUpdateAdapter<DocumentEntity> __deletionAdapterOfDocumentEntity;

  private final EntityDeletionOrUpdateAdapter<DocumentEntity> __updateAdapterOfDocumentEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final SharedSQLiteStatement __preparedStmtOfDeletePhotosByDocumentId;

  private final SharedSQLiteStatement __preparedStmtOfDeletePhotoByPath;

  public DocumentDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDocumentEntity = new EntityInsertionAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `documents` (`id`,`title`,`description`,`createdAt`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getDescription());
        statement.bindLong(4, entity.getCreatedAt());
      }
    };
    this.__insertionAdapterOfDocumentPhotoEntity = new EntityInsertionAdapter<DocumentPhotoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `document_photos` (`id`,`documentId`,`photoPath`,`orderIndex`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentPhotoEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getDocumentId());
        statement.bindString(3, entity.getPhotoPath());
        statement.bindLong(4, entity.getOrderIndex());
      }
    };
    this.__deletionAdapterOfDocumentEntity = new EntityDeletionOrUpdateAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `documents` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfDocumentEntity = new EntityDeletionOrUpdateAdapter<DocumentEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `documents` SET `id` = ?,`title` = ?,`description` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DocumentEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getDescription());
        statement.bindLong(4, entity.getCreatedAt());
        statement.bindLong(5, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM documents";
        return _query;
      }
    };
    this.__preparedStmtOfDeletePhotosByDocumentId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM document_photos WHERE documentId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeletePhotoByPath = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM document_photos WHERE photoPath = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final DocumentEntity document,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfDocumentEntity.insertAndReturnId(document);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertPhotos(final List<DocumentPhotoEntity> photos,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDocumentPhotoEntity.insert(photos);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final DocumentEntity document,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfDocumentEntity.handle(document);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final DocumentEntity document,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfDocumentEntity.handle(document);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertDocumentWithPhotos(final DocumentEntity document,
      final List<DocumentPhotoEntity> photos, final Continuation<? super Long> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> DocumentDao.DefaultImpls.insertDocumentWithPhotos(DocumentDao_Impl.this, document, photos, __cont), $completion);
  }

  @Override
  public Object updateDocumentWithPhotos(final DocumentEntity document,
      final List<DocumentPhotoEntity> photos, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> DocumentDao.DefaultImpls.updateDocumentWithPhotos(DocumentDao_Impl.this, document, photos, __cont), $completion);
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
  public Object deletePhotosByDocumentId(final long documentId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePhotosByDocumentId.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, documentId);
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
          __preparedStmtOfDeletePhotosByDocumentId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePhotoByPath(final String path, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePhotoByPath.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, path);
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
          __preparedStmtOfDeletePhotoByPath.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<DocumentWithPhotos>> getAll() {
    final String _sql = "SELECT * FROM documents ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"document_photos",
        "documents"}, new Callable<List<DocumentWithPhotos>>() {
      @Override
      @NonNull
      public List<DocumentWithPhotos> call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
            final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final LongSparseArray<ArrayList<DocumentPhotoEntity>> _collectionPhotos = new LongSparseArray<ArrayList<DocumentPhotoEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionPhotos.containsKey(_tmpKey)) {
                _collectionPhotos.put(_tmpKey, new ArrayList<DocumentPhotoEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipdocumentPhotosAscomExampleTimemanagerDataLocalEntityDocumentPhotoEntity(_collectionPhotos);
            final List<DocumentWithPhotos> _result = new ArrayList<DocumentWithPhotos>(_cursor.getCount());
            while (_cursor.moveToNext()) {
              final DocumentWithPhotos _item;
              final DocumentEntity _tmpDocument;
              final long _tmpId;
              _tmpId = _cursor.getLong(_cursorIndexOfId);
              final String _tmpTitle;
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
              final String _tmpDescription;
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              _tmpDocument = new DocumentEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCreatedAt);
              final ArrayList<DocumentPhotoEntity> _tmpPhotosCollection;
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              _tmpPhotosCollection = _collectionPhotos.get(_tmpKey_1);
              _item = new DocumentWithPhotos(_tmpDocument,_tmpPhotosCollection);
              _result.add(_item);
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<DocumentWithPhotos> getById(final long id) {
    final String _sql = "SELECT * FROM documents WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, true, new String[] {"document_photos",
        "documents"}, new Callable<DocumentWithPhotos>() {
      @Override
      @Nullable
      public DocumentWithPhotos call() throws Exception {
        __db.beginTransaction();
        try {
          final Cursor _cursor = DBUtil.query(__db, _statement, true, null);
          try {
            final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
            final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
            final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
            final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
            final LongSparseArray<ArrayList<DocumentPhotoEntity>> _collectionPhotos = new LongSparseArray<ArrayList<DocumentPhotoEntity>>();
            while (_cursor.moveToNext()) {
              final long _tmpKey;
              _tmpKey = _cursor.getLong(_cursorIndexOfId);
              if (!_collectionPhotos.containsKey(_tmpKey)) {
                _collectionPhotos.put(_tmpKey, new ArrayList<DocumentPhotoEntity>());
              }
            }
            _cursor.moveToPosition(-1);
            __fetchRelationshipdocumentPhotosAscomExampleTimemanagerDataLocalEntityDocumentPhotoEntity(_collectionPhotos);
            final DocumentWithPhotos _result;
            if (_cursor.moveToFirst()) {
              final DocumentEntity _tmpDocument;
              final long _tmpId;
              _tmpId = _cursor.getLong(_cursorIndexOfId);
              final String _tmpTitle;
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
              final String _tmpDescription;
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
              final long _tmpCreatedAt;
              _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
              _tmpDocument = new DocumentEntity(_tmpId,_tmpTitle,_tmpDescription,_tmpCreatedAt);
              final ArrayList<DocumentPhotoEntity> _tmpPhotosCollection;
              final long _tmpKey_1;
              _tmpKey_1 = _cursor.getLong(_cursorIndexOfId);
              _tmpPhotosCollection = _collectionPhotos.get(_tmpKey_1);
              _result = new DocumentWithPhotos(_tmpDocument,_tmpPhotosCollection);
            } else {
              _result = null;
            }
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            _cursor.close();
          }
        } finally {
          __db.endTransaction();
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

  private void __fetchRelationshipdocumentPhotosAscomExampleTimemanagerDataLocalEntityDocumentPhotoEntity(
      @NonNull final LongSparseArray<ArrayList<DocumentPhotoEntity>> _map) {
    if (_map.isEmpty()) {
      return;
    }
    if (_map.size() > RoomDatabase.MAX_BIND_PARAMETER_CNT) {
      RelationUtil.recursiveFetchLongSparseArray(_map, true, (map) -> {
        __fetchRelationshipdocumentPhotosAscomExampleTimemanagerDataLocalEntityDocumentPhotoEntity(map);
        return Unit.INSTANCE;
      });
      return;
    }
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT `id`,`documentId`,`photoPath`,`orderIndex` FROM `document_photos` WHERE `documentId` IN (");
    final int _inputSize = _map.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _stmt = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (int i = 0; i < _map.size(); i++) {
      final long _item = _map.keyAt(i);
      _stmt.bindLong(_argIndex, _item);
      _argIndex++;
    }
    final Cursor _cursor = DBUtil.query(__db, _stmt, false, null);
    try {
      final int _itemKeyIndex = CursorUtil.getColumnIndex(_cursor, "documentId");
      if (_itemKeyIndex == -1) {
        return;
      }
      final int _cursorIndexOfId = 0;
      final int _cursorIndexOfDocumentId = 1;
      final int _cursorIndexOfPhotoPath = 2;
      final int _cursorIndexOfOrderIndex = 3;
      while (_cursor.moveToNext()) {
        final long _tmpKey;
        _tmpKey = _cursor.getLong(_itemKeyIndex);
        final ArrayList<DocumentPhotoEntity> _tmpRelation = _map.get(_tmpKey);
        if (_tmpRelation != null) {
          final DocumentPhotoEntity _item_1;
          final long _tmpId;
          _tmpId = _cursor.getLong(_cursorIndexOfId);
          final long _tmpDocumentId;
          _tmpDocumentId = _cursor.getLong(_cursorIndexOfDocumentId);
          final String _tmpPhotoPath;
          _tmpPhotoPath = _cursor.getString(_cursorIndexOfPhotoPath);
          final int _tmpOrderIndex;
          _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
          _item_1 = new DocumentPhotoEntity(_tmpId,_tmpDocumentId,_tmpPhotoPath,_tmpOrderIndex);
          _tmpRelation.add(_item_1);
        }
      }
    } finally {
      _cursor.close();
    }
  }
}
