package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavProvider

fun CaldavAccountEntity.toDomain(): CaldavAccount = CaldavAccount(
    id = id,
    provider = runCatching { CaldavProvider.valueOf(provider) }.getOrDefault(CaldavProvider.YANDEX),
    serverUrl = serverUrl,
    username = username,
    collectionUrl = collectionUrl,
    collectionName = collectionName,
    lastSyncAt = lastSyncAt,
    lastError = lastError,
)
