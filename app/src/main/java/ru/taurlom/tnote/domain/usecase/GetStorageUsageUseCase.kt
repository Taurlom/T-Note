package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.StorageUsage
import ru.taurlom.tnote.domain.repository.StorageStatsRepository
import javax.inject.Inject

/** Снимок использования хранилища: размер фото, документов, базы и прочего. */
class GetStorageUsageUseCase @Inject constructor(private val repository: StorageStatsRepository) {
    suspend operator fun invoke(): StorageUsage = repository.storageUsage()
}
