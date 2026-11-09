package ru.taurlom.tnote.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.security.PasswordStore
import ru.taurlom.tnote.data.sync.CaldavApi
import ru.taurlom.tnote.data.sync.CaldavSyncEngine
import ru.taurlom.tnote.data.sync.CaldavSyncScheduler
import ru.taurlom.tnote.data.sync.SyncOverwriteBus
import ru.taurlom.tnote.data.sync.SyncTransaction
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.repository.CaldavRepository
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CalDAV-репозиторий: мост между настройками (UI), сетевым клиентом,
 * движком и таблицами БД. Пароль ходит только через [PasswordStore].
 * Подключение/отключение заведует также периодическим воркером, а прогон
 * синка анонсирует перезаписи через [SyncOverwriteBus] («не молча»).
 */
@Singleton
class CaldavRepositoryImpl @Inject constructor(
    private val caldavDao: CaldavDao,
    private val scheduledEventDao: ScheduledEventDao,
    private val tombstoneDao: CaldavTombstoneDao,
    private val api: CaldavApi,
    private val engine: CaldavSyncEngine,
    private val passwordStore: PasswordStore,
    private val scheduler: CaldavSyncScheduler,
    private val tx: SyncTransaction,
) : CaldavRepository {

    /**
     * Периодический и экспедированный воркеры могут сработать почти
     * одновременно: два движка поверх одной коллекции — гонка вычитки
     * и постановки. Сериализуем на мьютексе; транзакции Room порядок
     * сами не дадут перепутать, но double-pull между ними возможен.
     */
    private val syncLock = Mutex()

    override fun observeAccounts(): Flow<List<CaldavAccount>> = caldavDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun discover(server: String, username: String, password: String): List<CaldavCalendarRef> =
        api.discoverCalendars(server, username, password)

    override suspend fun connect(server: String, username: String, password: String, calendar: CaldavCalendarRef): Long {
        val id = caldavDao.insert(
            CaldavAccountEntity(
                serverUrl = server.trimEnd('/'),
                username = username,
                collectionUrl = calendar.url,
                collectionName = calendar.name,
                lastSyncAt = 0,
                lastError = null,
            ),
        )
        passwordStore.save(username, password)
        scheduler.ensurePeriodic()

        // Первая синхронизация сразу же: успех — события уже на сетке,
        // отказ — честная запись в lastError, аккаунт остаётся подключённым.
        try {
            sync(id)
        } catch (_: CaldavSyncException) {
            // причина уже записана в lastError внутри [sync]
        }
        return id
    }

    override suspend fun disconnect(accountId: Long) {
        val account = caldavDao.getById(accountId) ?: return
        tx.inTransaction {
            scheduledEventDao.deleteByCaldavAccount(accountId)
            // Tombstones этого аккаунта гасятся вместе с подпиской: его
            // события остаются на сервере, а очередь DELETE без подписки
            // превращается в мусор, который некому исполнить.
            tombstoneDao.deleteAllFor(accountId)
            caldavDao.deleteById(accountId)
        }
        // Пароль хранится по username: другие подписки этого аккаунта остаются.
        val remaining = caldavDao.getAll()
        if (remaining.none { it.username == account.username }) {
            passwordStore.clear(account.username)
        }
        if (remaining.isEmpty()) scheduler.cancelPeriodic()
    }
    override suspend fun sync(accountId: Long): CaldavSyncResult {
        val account = caldavDao.getById(accountId) ?: return CaldavSyncResult(0, 0, 0)
        val password = passwordStore.read(account.username)
            ?: throw CaldavSyncException.Auth("Пароль для ${account.username} не сохранён")
        return try {
            // Перезаписи («сервер новее») анонсируются после удачного прогона:
            // пользователь должен увидеть это на любом экране, а не только
            // в настройках.
            syncLock.withLock { engine.sync(account, password) }
                .also { SyncOverwriteBus.report(it.overwritten) }
        } catch (e: CaldavSyncException) {
            caldavDao.markFailed(account.id, e.message)
            throw e
        }
    }

    override suspend fun syncAll(): CaldavSyncResult = caldavDao.getAll()
        .fold(CaldavSyncResult(0, 0, 0)) { acc, account ->
            runCatching { sync(account.id) }.fold(
                onSuccess = { acc + it },
                onFailure = { acc },
            )
        }
}
