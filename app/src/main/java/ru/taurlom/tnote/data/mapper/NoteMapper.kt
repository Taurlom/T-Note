package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.NoteEntity
import ru.taurlom.tnote.data.local.entity.NotePhotoEntity
import ru.taurlom.tnote.data.local.entity.NoteWithPhotos
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.util.NoteTags

fun NoteWithPhotos.toDomain(): Note = Note(
    id = note.id,
    title = note.title,
    content = note.content,
    createdAt = note.createdAt,
    position = note.position,
    photoPaths = photos.sortedBy { it.orderIndex }.map { it.photoPath },
    tags = NoteTags.parseStored(note.tags),
)

// Замечание: у NoteEntity нет toDomain() — домен всегда читается через
// NoteWithPhotos, иначе фото молча терялись бы при мерже в UpdateNoteUseCase.

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    content = content,
    createdAt = createdAt,
    position = position,
    tags = NoteTags.toStored(tags),
)

fun Note.toPhotoEntities(): List<NotePhotoEntity> = photoPaths.mapIndexed { index, path ->
    NotePhotoEntity(noteId = id, photoPath = path, orderIndex = index)
}
