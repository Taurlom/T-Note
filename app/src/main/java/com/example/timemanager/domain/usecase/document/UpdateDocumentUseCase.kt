package com.example.timemanager.domain.usecase.document

import android.net.Uri
import com.example.timemanager.data.local.DocumentPhotoSaver
import com.example.timemanager.domain.model.Document
import com.example.timemanager.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class UpdateDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val photoSaver: DocumentPhotoSaver
) {
    suspend operator fun invoke(
        document: Document,
        newPhotoUris: List<Uri> = emptyList(),
        removedPhotoPaths: List<String> = emptyList()
    ) {
        // Файл ложится на диск до записи в базу: теперь это обязательный
        // порядок — база может сослаться только на существующий файл.
        val newPaths = photoSaver.savePhotos(newPhotoUris)

        // Как в renamePhotoPath: список фото берём из свежего снапшота базы,
        // из UI применяем только заголовок и описание — параллельный кроп
        // больше не перетирается устаревшими путями.
        val fresh = repository.getById(document.id).first() ?: document
        val updatedPaths = fresh.photoPaths
            .filterNot { it in removedPhotoPaths }
            .plus(newPaths)
        repository.update(
            fresh.copy(
                title = document.title,
                description = document.description,
                photoPaths = updatedPaths
            )
        )

        // Удаляем файлы только после коммита базы и только те, на которые
        // не осталось ссылок (вдруг их параллельно вернул другой редактор).
        photoSaver.deletePhotos(removedPhotoPaths.filterNot { it in updatedPaths })
    }

    /**
     * Точечная замена пути фото (кроп). Читает документ свежим из базы, а не
     * из снапшота UI: запись по устаревшему снапшоту перетирала соседние
     * правки и роняла ссылки на фото (файлы оставались сиротами).
     */
    suspend fun renamePhotoPath(documentId: Long, oldPath: String, newPath: String) {
        val fresh = repository.getById(documentId).first() ?: return
        val updated = fresh.copy(
            photoPaths = fresh.photoPaths.map { if (it == oldPath) newPath else it }
        )
        repository.update(updated)
        photoSaver.deletePhotos(listOf(oldPath))
    }
}
