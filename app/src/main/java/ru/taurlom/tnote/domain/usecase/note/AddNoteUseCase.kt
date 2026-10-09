package ru.taurlom.tnote.domain.usecase.note

import ru.taurlom.tnote.di.NotePhotos
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import ru.taurlom.tnote.domain.repository.PhotoStorage
import ru.taurlom.tnote.domain.util.NoteImageRefs
import java.time.Clock
import javax.inject.Inject

class AddNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    @NotePhotos private val photoStorage: PhotoStorage,
    private val clock: Clock,
) {
    /**
     * Позиция — из базы (max + 1), а не из снапшота UI; файлы фото ложатся
     * до записи: база ссылается только на существующее. Ссылки на новые фото
     * в тексте (content:// URI из редактора) переписываются на сохранённые
     * относительные пути, список фото заметки — из ссылок текста.
     */
    suspend operator fun invoke(note: Note, newPhotoUris: List<String> = emptyList()): Long {
        // Каждый URI сохраняется отдельно: нужны пары «источник → файл» для
        // переписи ссылок, mapNotNull по общему списку потерял бы соответствие.
        val replacements = newPhotoUris.mapNotNull { uri ->
            photoStorage.savePhotos(listOf(uri)).firstOrNull()?.let { uri to it }
        }.toMap()
        val content = NoteImageRefs.replace(note.content, replacements)
        return repository.insert(
            note.copy(
                createdAt = clock.millis(),
                position = repository.getMaxPosition() + 1,
                content = content,
                photoPaths = NoteImageRefs.extractAll(content),
            ),
        )
    }
}
