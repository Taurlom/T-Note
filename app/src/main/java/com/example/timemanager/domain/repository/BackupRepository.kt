package com.example.timemanager.domain.repository

import android.net.Uri

/**
 * Экспорт/импорт резервной копии всех пользовательских данных одним zip:
 * база Room, фотографии документов, выбранные тема и шрифт.
 *
 * Uri — SAF-адрес (файл, выбранный пользователем в «Сохранить как…» /
 * «Открыть»): приложение остаётся офлайн, без сетевых разрешений.
 */
interface BackupRepository {

    /**
     * Переписывает [target] свежей резервной копией.
     *
     * Возвращает сводку: сколько фото попало в копию и какие упомянутые в
     * базе файлы НЕ найдены на устройстве (такие в копию не попадают —
     * вызывающий обязан предупредить пользователя).
     */
    suspend fun exportBackup(target: Uri): BackupSummary

    /** Метаданные копии для диалога подтверждения; null если это не копия. */
    suspend fun describeBackup(source: Uri): BackupDescription?

    /**
     * Заменяет данные приложения содержимым копии из [source].
     *
     * Отказ — [BackupImportException] (наследник [IllegalStateException]).
     * Все проверки (формат, SQLite-заголовок, версия схемы, лимиты
     * распаковки) выполняются ДО изменения живых данных; сама подмена
     * происходит «транзакцией» с откатом при сбое — наполовину
     * заменённых данных не остаётся. После импорта нужен перезапуск
     * процесса.
     */
    suspend fun importBackup(source: Uri): BackupSummary
}

/**
 * Причина отказа импорта. Типизирована, а не строка: экран подбирает
 * локализуемое сообщение по виду ошибки, слой данных не знает языков
 * интерфейса. Текст исключения — для логов (английский), пользователю
 * он не показывается.
 */
sealed class BackupImportException(message: String) : IllegalStateException(message) {

    /** В архиве нет manifest.json — файл не является копией T-Note. */
    data object NotABackup :
        BackupImportException("manifest.json is missing: not a T-Note backup")

    /** Формат копии новее поддерживаемого (manifest.formatVersion). */
    class UnsupportedFormat(actual: Int) :
        BackupImportException("Unsupported backup format version: $actual")

    /** В копии нет файла базы данных. */
    data object NoDatabase : BackupImportException("Backup contains no database")

    /** Файл базы не читается как SQLite или версия схемы в нём нулевая. */
    data object InvalidDatabase :
        BackupImportException("Database file is not a valid SQLite database")

    /** Схема БД в копии новее текущей: копия от более новой версии приложения. */
    class NewerSchema(found: Int, current: Int) :
        BackupImportException("Backup schema version $found is newer than supported $current")

    /** Схема БД в копии старше минимально поддерживаемой цепочкой миграций. */
    class UnsupportedSchema(found: Int, minimum: Int) :
        BackupImportException("Backup schema version $found is older than the minimum supported $minimum")

    /** Распакованный объём превысил лимит — защита от zip-бомбы. */
    data object TooLarge :
        BackupImportException("Unpacked backup exceeds the size limit")
}

/** Итог экспорта/импорта: фото в копии и потери, известные ещё на экспорте. */
data class BackupSummary(
    val photos: Int,
    val missingPhotos: List<String> = emptyList()
)

/** Шапка копии: чтобы пользователь подтверждал восстановление осознанно. */
data class BackupDescription(
    val createdAt: Long,
    val appVersion: String,
    val photos: Int,
    val missingPhotos: Int
)
