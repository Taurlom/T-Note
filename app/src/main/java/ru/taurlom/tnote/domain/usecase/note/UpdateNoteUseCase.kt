package ru.taurlom.tnote.domain.usecase.note

import android.net.Uri
import ru.taurlom.tnote.data.local.NotePhotoSaver
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.repository.NoteRepository
import javax.inject.Inject

class UpdateNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
    private val photoSaver: NotePhotoSaver
) {
    /**
     * Тот же безопасный порядок, что у документов после фикса гонок:
     * новые файлы — до записи, удаление старых — только после коммита базы
     * и только без оставшихся ссылок. Поля заголовка/текста берёт редактор,
     * остальное (position, createdAt, фото из параллельной правки) — свежий
     * снапшот базы.
     */
    suspend operator fun invoke(
        note: Note,
        newPhotoUris: List<Uri> = emptyList(),
        removedPhotoPaths: List<String> = emptyList()
    ) {
        val newPaths = photoSaver.savePhotos(newPhotoUris)
        val fresh = repository.getByIdOnce(note.id) ?: note
        val updatedPaths = fresh.photoPaths
            .filterNot { it in removedPhotoPaths }
            .plus(newPaths)
        repository.update(
            fresh.copy(
                title = note.title,
                content = note.content,
                photoPaths = updatedPaths
            )
        )
        photoSaver.deletePhotos(removedPhotoPaths.filterNot { it in updatedPaths })
    }
}
