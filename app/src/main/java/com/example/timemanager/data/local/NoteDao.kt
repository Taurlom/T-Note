package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.timemanager.data.local.entity.NoteEntity
import com.example.timemanager.data.local.entity.NotePhotoEntity
import com.example.timemanager.data.local.entity.NoteWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM notes ORDER BY position ASC, createdAt DESC")
    fun getAll(): Flow<List<NoteWithPhotos>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun getById(id: Long): Flow<NoteWithPhotos?>

    /** Одноразовое чтение ВМЕСТЕ с фото — нужно для мержа при обновлении. */
    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getByIdOnce(id: Long): NoteWithPhotos?

    @Query("SELECT COALESCE(MAX(position), -1) FROM notes")
    suspend fun getMaxPosition(): Int

    /** Как у документов: один транзакционный прогон, без промежуточных состояний. */
    @Transaction
    suspend fun updatePositions(notes: List<NoteEntity>) {
        notes.forEach { update(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<NotePhotoEntity>)

    @Query("DELETE FROM note_photos WHERE noteId = :noteId")
    suspend fun deletePhotosByNoteId(noteId: Long)

    @Query("DELETE FROM note_photos")
    suspend fun deleteAllPhotos()

    @Transaction
    suspend fun insertNoteWithPhotos(
        note: NoteEntity,
        photos: List<NotePhotoEntity>
    ): Long {
        val noteId = insert(note)
        insertPhotos(
            photos.mapIndexed { index, photo ->
                photo.copy(noteId = noteId, orderIndex = index)
            }
        )
        return noteId
    }

    @Transaction
    suspend fun updateNoteWithPhotos(
        note: NoteEntity,
        photos: List<NotePhotoEntity>
    ) {
        update(note)
        deletePhotosByNoteId(note.id)
        insertPhotos(
            photos.mapIndexed { index, photo ->
                photo.copy(noteId = note.id, orderIndex = index)
            }
        )
    }
}
