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
     * Бросает [IllegalStateException], если файл не является корректной
     * копией T-Note. После импорта нужен перезапуск процесса.
     */
    suspend fun importBackup(source: Uri): BackupSummary
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
