package ru.taurlom.tnote.data.repository

import ru.taurlom.tnote.data.local.NoteDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.data.mapper.toPhotoEntities
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao
) : NoteRepository {

    override fun getAll(): Flow<List<Note>> =
        noteDao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getById(id: Long): Flow<Note?> =
        noteDao.getById(id).map { it?.toDomain() }

    override suspend fun getByIdOnce(id: Long): Note? =
        noteDao.getByIdOnce(id)?.toDomain()

    override suspend fun getMaxPosition(): Int = noteDao.getMaxPosition()

    override suspend fun insert(note: Note): Long =
        noteDao.insertNoteWithPhotos(note.toEntity(), note.toPhotoEntities())

    override suspend fun update(note: Note) =
        noteDao.updateNoteWithPhotos(note.toEntity(), note.toPhotoEntities())

    override suspend fun updatePositions(notes: List<Note>) =
        noteDao.updatePositions(notes.map { it.toEntity() })

    /** Удаление строки каскадом снимает и фото-записи (FK ON DELETE CASCADE). */
    override suspend fun delete(note: Note) = noteDao.delete(note.toEntity())

    override suspend fun deleteAll() {
        noteDao.deleteAllPhotos()
        noteDao.deleteAll()
    }
}
