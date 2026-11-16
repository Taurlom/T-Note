package ru.taurlom.tnote.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.security.PasswordStore
import ru.taurlom.tnote.data.security.httpsOriginOf
import ru.taurlom.tnote.data.security.originOf
import ru.taurlom.tnote.data.sync.CaldavApi
import ru.taurlom.tnote.data.sync.CaldavSyncEngine
import ru.taurlom.tnote.data.sync.CaldavSyncScheduler
import ru.taurlom.tnote.data.sync.SyncOverwriteBus
import ru.taurlom.tnote.data.sync.SyncTransaction
import ru.taurlom.tnote.data.sync.google.GoogleCalendarApi
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavProvider
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.repository.CaldavRepository
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Секрет Google-аккаунтов: OAuth-токен берётся на лету, хранить нечего,
 * но место пароля в PasswordStore должно быть занято — движок читает
 * секрет по username, не зная о провайдере.
 */
private const val GOOGLE_PASSWORD_PLACEHOLDER = "google-oauth"

/**
 * Репозиторий синхронизации: мост между настройками (UI), транспортами
 * провайдеров, движком и таблицами БД. Секрет ходит только через
 * [PasswordStore] и привязан к паре (origin сервера, логин): адрес сервера
 * живёт в базе, а база приходит из резервной копии, — без привязки пароль
 * уехал бы на сервер из чужой копии. Рядом с привязкой — проверка, что
 * адрес коллекции принадлежит серверу аккаунта ([passwordFor]), и отказ
 * транспорта от чужих origin (CaldavClient.resolve). Подключение/отключение
 * заведует также периодическим воркером, а прогон синка анонсирует
 * перезаписи через [SyncOverwriteBus] («не молча»). Транспорт выбирается
 * провайдером аккаунта: Яндекс — CalDAV, Google — Calendar API (оба —
 * [CaldavApi]).
 */
@Singleton
class CaldavRepositoryImpl @Inject constructor(
    private val caldavDao: CaldavDao,
    private val scheduledEventDao: ScheduledEventDao,
    private val noteDao: CalendarNoteDao,
    private val tombstoneDao: CaldavTombstoneDao,
    private val api: CaldavApi,
    private val googleApi: GoogleCalendarApi,
    private val engine: CaldavSyncEngine,
    private val passwordStore: PasswordStore,
    private val scheduler: CaldavSyncScheduler,
    private val tx: SyncTransaction,
) : CaldavRepository {

    /**
     * Координатор жизни подписки: подключение, отключение, выключение после
     * восстановления копии, прогон движка и приписка «старья» идут под одним
     * мьютексом. Транзакций Room для этого мало: движок держит вычитку в сети
     * секунды, и disconnect, вклинившийся между нею и применением, оставил бы
     * в базе строки с уже несуществующим accountId — их больше никто не
     * почистит (diff-удаления и очереди PUT/DELETE привязаны к аккаунту, а
     * attributeAll берёт только строки без приписки), а их старые href/etag
     * отравили бы следующую подписку: PUT по чужому адресу рвёт прогон
     * (requireAccountOrigin). Периодический и экспедированный воркеры тоже
     * бегают здесь: два движка поверх одной коллекции — гонка вычитки и
     * постановки.
     */
    private val lifecycleLock = Mutex()

    override fun observeAccounts(): Flow<List<CaldavAccount>> = caldavDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun discover(provider: CaldavProvider, server: String, username: String, password: String): List<CaldavCalendarRef> {
        // Пароль в этой операции уже пользовательский ввод, но адрес всё
        // равно обязан быть https: форма подключения — то место, где
        // нешифрованный ход до сервера ещё можно остановить.
        if (httpsOriginOf(server) == null) {
            throw CaldavSyncException.Server("Адрес сервера должен быть https://…: $server")
        }
        return transport(provider).discoverCalendars(server, username, password)
    }

    override suspend fun connect(
        provider: CaldavProvider,
        server: String,
        username: String,
        password: String,
        calendar: CaldavCalendarRef,
    ): Long = lifecycleLock.withLock {
        // Секрет привязывается к origin сервера — https и разбор адреса
        // обязательны уже здесь: иначе пара (логин, сервер) не вычислима,
        // а подписка родилась бы с паролем, который нечем прочитать.
        val origin = httpsOriginOf(server)
            ?: throw CaldavSyncException.Server("Адрес сервера должен быть https://…: $server")
        if (provider == CaldavProvider.YANDEX && httpsOriginOf(calendar.url) != origin) {
            throw CaldavSyncException.Server("Адрес коллекции вне сервера: ${calendar.url}")
        }
        val id = caldavDao.insert(
            CaldavAccountEntity(
                provider = provider.name,
                serverUrl = server.trimEnd('/'),
                username = username,
                collectionUrl = calendar.url,
                collectionName = calendar.name,
                lastSyncAt = 0,
                lastError = null,
            ),
        )
        passwordStore.save(origin, username, password.ifBlank { GOOGLE_PASSWORD_PLACEHOLDER })
        scheduler.ensurePeriodic()
        // Локальные заметки (никогда не гулявшие по серверу) приписываются
        // новой подписке и едут первым же пушем — перенос заметок на другое
        // устройство не требует их правки.
        noteDao.attributeAll(id)

        // Первая синхронизация сразу же: успех — события уже на сетке,
        // отказ — честная запись в lastError, аккаунт остаётся подключённым.
        // Под тем же блоком мьютекса, что и рождение аккаунта: иначе фоновый
        // прогон успел бы вписаться между припиской и первым diff'ом.
        // syncLocked, а не sync: мьютекс уже удерживается, а он не реентабелен.
        try {
            syncLocked(id)
        } catch (_: CaldavSyncException) {
            // причина уже записана в lastError внутри [syncLocked]
        }
        id
    }

    override suspend fun disconnect(accountId: Long, keepEvents: Boolean) = lifecycleLock.withLock {
        val account = caldavDao.getById(accountId) ?: return@withLock
        tx.inTransaction {
            if (keepEvents) {
                // События остаются в приложении как локальные: отвязка стирает
                // приписку и серверную привязку, UID переживает — при повторном
                // подключении вычитка сопоставит строки по UID без дублей.
                scheduledEventDao.detachFromAccount(accountId)
            } else {
                scheduledEventDao.deleteByCaldavAccount(accountId)
            }
            // Заметки отвязываются в обеих ветках — единая политика: они
            // пользовательский текст, а не «событие источника», и удалять их
            // молча нельзя. Оставленная приписка к выключенной подписке
            // хуже, чем у событий: attributeAll при следующем подключении
            // берёт только строки с NULL, поэтому заметка навсегда осталась бы
            // привязана к мёртвому аккаунту — её PUT не уезжает (очередь
            // читается по аккаунту), вычитка её не чистит, а stale href/etag
            // всплывают при первой же правке новой подписки. Отвязка чистит
            // accountId, dirty, href и ETag — заметка становится локальной.
            noteDao.detachFromAccount(accountId)
            // Tombstones этого аккаунта гасятся вместе с подпиской: его
            // события остаются на сервере, а очередь DELETE без подписки
            // превращается в мусор, который некому исполнить.
            tombstoneDao.deleteAllFor(accountId)
            caldavDao.deleteById(accountId)
        }
        // Секрет привязан к паре (origin сервера, логин): другие подписки
        // этого же сервера и логина остаются.
        val remaining = caldavDao.getAll()
        if (remaining.none { it.username == account.username }) {
            // Ключ берётся из адреса аккаунта; адрес, который разложить не
            // чем (старые записи), чистится как есть — лишнего не удалит.
            passwordStore.clear(originOf(account.serverUrl) ?: account.serverUrl, account.username)
        }
        if (remaining.isEmpty()) scheduler.cancelPeriodic()
    }

    override suspend fun deactivateSubscriptions(): Int = lifecycleLock.withLock {
        // Восстановление резервной копии меняет базу, а секреты живут
        // отдельно: оставь мы подписки живыми, фоновый синк отправил бы
        // сохранённый пароль на сервер из принесённой копии. Поэтому
        // импортированные подписки выключаются (события и заметки остаются
        // локальными строками), все секреты стираются, а фоновые прогоны
        // снимаются — синхронизация продолжается только после нового
        // подключения с вводом пароля.
        passwordStore.clearAll()
        scheduler.cancelAll()
        val accounts = caldavDao.getAll()
        accounts.forEach { account ->
            tx.inTransaction {
                // Отвязка вместо удаления: данные копии принадлежат
                // пользователю, а серверная привязка (href, ETag) и очередь
                // отправки — нет: по ним PUT/DELETE ушли бы на чужой адрес.
                scheduledEventDao.detachFromAccount(account.id)
                noteDao.detachFromAccount(account.id)
                tombstoneDao.deleteAllFor(account.id)
                caldavDao.deleteById(account.id)
            }
        }
        accounts.size
    }

    override suspend fun sync(accountId: Long): CaldavSyncResult = lifecycleLock.withLock { syncLocked(accountId) }

    /** Прогон под [lifecycleLock]: отдельная функция, потому что [connect] держит блок сам. */
    private suspend fun syncLocked(accountId: Long): CaldavSyncResult {
        val account = caldavDao.getById(accountId) ?: return CaldavSyncResult(0, 0, 0)
        return try {
            val password = passwordFor(account)
            // Перезаписи («сервер новее») анонсируются после удачного прогона:
            // пользователь должен увидеть это на любом экране, а не только
            // в настройках.
            engine.sync(account, password, transport(account))
                .also { SyncOverwriteBus.report(it.overwritten) }
        } catch (e: CaldavSyncException) {
            caldavDao.markFailed(account.id, e.message)
            throw e
        } catch (e: Exception) {
            // Непредсказуемый сбой (битый JSON и прочее) не должен ронять
            // приложение и терять причину: переводим в типизированный вид,
            // карточка покажет.
            caldavDao.markFailed(account.id, e.message ?: e.javaClass.simpleName)
            throw CaldavSyncException.Server(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Секрет аккаунта вместе с проверкой адресов: пароль отдаётся только
     * когда сервер аккаунта — https, а адрес коллекции принадлежит тому же
     * origin (у Google коллекция — calendarId, а не URL; путь запросов там
     * фиксирован кодом транспорта). Подмена адреса в базе — импорт копии или
     * правка руками — не должна увести сохранённый пароль на чужой хост.
     */
    private fun passwordFor(account: CaldavAccountEntity): String {
        val origin = httpsOriginOf(account.serverUrl)
            ?: throw CaldavSyncException.Server("Адрес сервера не https: ${account.serverUrl}")
        if (providerOf(account) == CaldavProvider.YANDEX && httpsOriginOf(account.collectionUrl) != origin) {
            throw CaldavSyncException.Server("Адрес коллекции вне сервера: ${account.collectionUrl}")
        }
        return passwordStore.read(origin, account.username)
            ?: throw CaldavSyncException.Auth("Пароль для ${account.username} не сохранён")
    }

    override suspend fun syncAll(): CaldavSyncResult = caldavDao.getAll()
        .fold(CaldavSyncResult(0, 0, 0)) { acc, account ->
            runCatching { sync(account.id) }.fold(
                onSuccess = { acc + it },
                onFailure = { acc },
            )
        }

    override fun observeLocalOnlyCount(): Flow<Int> = scheduledEventDao.observeLocalOnlyCount()

    /** Транспорт по провайдеру аккаунта. */
    private fun transport(provider: CaldavProvider): CaldavApi = when (provider) {
        CaldavProvider.YANDEX -> api
        CaldavProvider.GOOGLE -> googleApi
    }

    private fun providerOf(account: CaldavAccountEntity): CaldavProvider =
        runCatching { CaldavProvider.valueOf(account.provider) }.getOrDefault(CaldavProvider.YANDEX)

    private fun transport(account: CaldavAccountEntity): CaldavApi = transport(providerOf(account))

    override suspend fun publishLocalOnly(accountId: Long): Int = lifecycleLock.withLock {
        // Аккаунт проверяется под тем же блоком, что и приписка: карточка
        // подписки могла быть отключена, пока пользователь нажимал кнопку, —
        // приписка к мёртвому id поставила бы события в очередь, которую
        // больше никто не читает (getDirtyFor привязан к аккаунту).
        if (caldavDao.getById(accountId) == null) return@withLock 0
        val count = scheduledEventDao.attributeAll(accountId)
        if (count > 0) scheduler.requestPush()
        count
    }
}
