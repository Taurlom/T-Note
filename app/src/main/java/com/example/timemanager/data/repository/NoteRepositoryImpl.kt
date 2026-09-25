package com.example.timemanager.data.repository

import com.example.timemanager.data.local.NoteDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.domain.model.Note
import com.example.timemanager.domain.repository.NoteRepository
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

    override suspend fun getMaxPosition(): Int = noteDao.getMaxPosition()

    override suspend fun insert(note: Note): Long = noteDao.insert(note.toEntity())

    override suspend fun update(note: Note) = noteDao.update(note.toEntity())

    override suspend fun updatePositions(notes: List<Note>) =
        noteDao.updatePositions(notes.map { it.toEntity() })

    override suspend fun delete(note: Note) = noteDao.delete(note.toEntity())

    override suspend fun deleteAll() = noteDao.deleteAll()
}
