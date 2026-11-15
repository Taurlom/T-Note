package ru.taurlom.tnote.domain.repository

import ru.taurlom.tnote.domain.model.StorageUsage

/**
 * Размер файлов приложения по группам (фото, документы, база, прочее).
 * Считывается по запросу — размеры файлов меняются вне контроля Flow,
 * вкладка «Данные» перечитывает их при каждом открытии.
 */
interface StorageStatsRepository {
    suspend fun storageUsage(): StorageUsage
}
