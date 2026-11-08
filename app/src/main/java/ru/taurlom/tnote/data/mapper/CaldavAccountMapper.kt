package ru.taurlom.tnote.data.mapper

import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.domain.model.CaldavAccount

fun CaldavAccountEntity.toDomain(): CaldavAccount = CaldavAccount(
    id = id,
    serverUrl = serverUrl,
    username = username,
    collectionUrl = collectionUrl,
    collectionName = collectionName,
    lastSyncAt = lastSyncAt,
    lastError = lastError,
)
