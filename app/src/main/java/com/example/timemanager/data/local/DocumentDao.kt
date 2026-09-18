package com.example.timemanager.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.timemanager.data.local.entity.DocumentEntity
import com.example.timemanager.data.local.entity.DocumentPhotoEntity
import com.example.timemanager.data.local.entity.DocumentWithPhotos
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Transaction
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DocumentWithPhotos>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun getById(id: Long): Flow<DocumentWithPhotos?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("DELETE FROM documents")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<DocumentPhotoEntity>)

    @Query("DELETE FROM document_photos WHERE documentId = :documentId")
    suspend fun deletePhotosByDocumentId(documentId: Long)

    @Query("DELETE FROM document_photos WHERE photoPath = :path")
    suspend fun deletePhotoByPath(path: String)

    @Transaction
    suspend fun insertDocumentWithPhotos(
        document: DocumentEntity,
        photos: List<DocumentPhotoEntity>
    ): Long {
        val documentId = insert(document)
        insertPhotos(
            photos.mapIndexed { index, photo ->
                photo.copy(documentId = documentId, orderIndex = index)
            }
        )
        return documentId
    }

    @Transaction
    suspend fun updateDocumentWithPhotos(
        document: DocumentEntity,
        photos: List<DocumentPhotoEntity>
    ) {
        update(document)
        deletePhotosByDocumentId(document.id)
        insertPhotos(
            photos.mapIndexed { index, photo ->
                photo.copy(documentId = document.id, orderIndex = index)
            }
        )
    }
}
