package ru.taurlom.tnote.domain.repository

/**
 * Хранилище фотографий вложений (документов, заметок) во внутренней памяти.
 *
 * Живёт на границе domain: use-case'ы работают с ним, не зная о data-слое;
 * реализации — в data ([...data.local.DocumentPhotoSaver] /
 * [...data.local.NotePhotoSaver]), привязка через Hilt-квалификаторы
 * (директории у документов и заметок разные).
 *
 * Content-URI передаются строками, чтобы domain не зависел от android.net.Uri
 * и оставался тестируемым на JVM.
 */
interface PhotoStorage {

    /**
     * Копирует фото по content-URI во внутреннее хранилище; возвращает
     * относительные пути сохранённых файлов (недоступные URI пропускаются).
     */
    suspend fun savePhotos(contentUris: List<String>): List<String>

    /** Удаляет файлы по относительным путям; отсутствующие игнорируются. */
    suspend fun deletePhotos(relativePaths: List<String>)
}
