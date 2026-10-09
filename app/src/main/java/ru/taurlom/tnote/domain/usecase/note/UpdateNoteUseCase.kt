package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.di.NotePhotos
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import ru.taurlom.tnote.domain.util.NoteImageRefs
import javax.inject.Inject

class UpdateNoteUseCase @Inject constructor(private val repository: NoteRepository, @NotePhotos private val photoStorage: PhotoStorage) {
    /**
     * Тот же безопасный порядок, что у документов после фикса гонок:
     * новые файлы — до записи, удаление старых — только после коммита базы.
     * Поля заголовка/текста берёт редактор, остальное (position, createdAt) —
     * свежий снапшот базы.
     *
     * Позиции фото — из ссылок в тексте: ссылки на content:// переписываются
     * на сохранённые пути, фото, исчезнувшее из текста, считается удалённым
     * и снимается с диска после коммита.
     */
    suspend operator fun invoke(note: Note, newPhotoUris: List<String> = emptyList()) {
        val replacements = newPhotoUris.mapNotNull { uri ->
            photoStorage.savePhotos(listOf(uri)).firstOrNull()?.let { uri to it }
        }.toMap()
        val content = NoteImageRefs.replace(note.content, replacements)
        val fresh = repository.getByIdOnce(note.id) ?: note
        val referenced = NoteImageRefs.extractAll(content)
        val removedPaths = fresh.photoPaths - referenced.toSet()
        repository.update(
            fresh.copy(
                title = note.title,
                content = content,
                photoPaths = referenced,
            ),
        )
        photoStorage.deletePhotos(removedPaths)
    }
}
