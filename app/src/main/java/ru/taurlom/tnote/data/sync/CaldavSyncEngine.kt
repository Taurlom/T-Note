package ru.taurlom.tnote.data.sync

import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Точечный доступ к транзакции Room: продакшен выполняет блок внутри
 * db.withTransaction, юнит-тесты движка — напрямую, без базы.
 */
fun interface SyncTransaction {
    suspend fun inTransaction(block: suspend () -> Unit)
}

/**
 * Движок синхронизации одного аккаунта: полная вычитка (calendar-query)
 * и применение разницы к таблице событий. Ключ сопоставления — UID:
 *  - событие есть на сервере и его нет у нас → insert (позиция — «максимум
 *    позиций этого дня» + 1);
 *  - есть и там, и там → update полей с сохранением id, позиции и
 *    привязки к аккаунту;
 *  - было импортировано этим аккаунтом, а на сервере исчезло → удаление.
 *
 * Полная вычитка вместо sync-token выбрана осознанно: Яндекс REPORT
 * sync-collection не поддерживает (проверено), календарь личный и небольшой.
 * Локальные события (caldavAccountId == null) движок не трогает.
 */
@Singleton
class CaldavSyncEngine @Inject constructor(
    private val eventDao: ScheduledEventDao,
    private val caldavDao: CaldavDao,
    private val api: CaldavApi,
    private val tx: SyncTransaction,
    private val clock: Clock,
) {

    suspend fun sync(account: CaldavAccountEntity, password: String): CaldavSyncResult {
        val pulled = api.pullEvents(account.collectionUrl, account.username, password)
        var added = 0
        var updated = 0
        var removed = 0
        tx.inTransaction {
            val uids = mutableListOf<String>()
            for (remote in pulled.events) {
                uids += remote.uid
                when (val existing = eventDao.getByUid(remote.uid)) {
                    null -> {
                        val position = eventDao.getMaxPosition(remote.date) + 1
                        eventDao.insert(
                            remote.toEntity().copy(
                                caldavAccountId = account.id,
                                position = position,
                            ),
                        )
                        added++
                    }

                    else -> {
                        // Импортированные события переписываются целиком с
                        // сервера: read-only, локальных пользовательских правок
                        // у них нет — сервер всегда источник истины.
                        eventDao.update(
                            remote.toEntity().copy(
                                id = existing.id,
                                caldavAccountId =
                                existing.caldavAccountId ?: account.id,
                                position = existing.position,
                            ),
                        )
                        updated++
                    }
                }
            }
            removed = eventDao.deleteNotInUids(account.id, uids)
            caldavDao.markSynced(account.id, clock.millis())
        }
        return CaldavSyncResult(added, updated, removed)
    }
}
