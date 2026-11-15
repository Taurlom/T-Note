package ru.taurlom.tnote.domain.model

/**
 * Использование хранилища приложением, снятое на момент запроса: четыре
 * группы файлов в [bytes] (суммарный размер файлов группы) и [files]
 * (их количество). «Прочее» — всё в filesDir, что не попало в другие группы.
 */
data class StorageUsage(
    /** Фото заметок (note_photos). */
    val photos: Group = Group(),
    /** Вложения документов (document_photos). */
    val documents: Group = Group(),
    /** Файлы базы Room: сама база, wal и shm. */
    val database: Group = Group(),
    /** Код приложения (APK), кэш и служебные файлы (DataStore и прочее в filesDir). */
    val other: Group = Group(),
) {

    /** Группа файлов хранилища. */
    data class Group(val bytes: Long = 0, val files: Int = 0)

    /** Суммарный размер всех групп. */
    val totalBytes: Long
        get() = photos.bytes + documents.bytes + database.bytes + other.bytes
}
