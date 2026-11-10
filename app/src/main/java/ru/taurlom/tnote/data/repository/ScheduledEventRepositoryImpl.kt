package ru.taurlom.tnote.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.toTombstone
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.data.sync.CaldavAttribution
import ru.taurlom.tnote.data.sync.CaldavSyncScheduler
import ru.taurlom.tnote.data.sync.SyncTransaction
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Репозиторий событий: помимо CRUD держит очередь двустороннего CalDAV-
 * обмена (этап 2). Правка приписанного события помечает строку dirty,
 * удаление пишет tombstone, а единственная активная подписка забирает
 * себе события без приписки (см. [CaldavAttribution]). Каждая попавшая
 * в очередь запись будит экспедированный синк — он же гоняется по
 * расписанию воркером.
 */
class ScheduledEventRepositoryImpl @Inject constructor(
    private val eventDao: ScheduledEventDao,
    private val caldavDao: CaldavDao,
    private val tombstoneDao: CaldavTombstoneDao,
    private val syncScheduler: CaldavSyncScheduler,
    private val tx: SyncTransaction,
    private val clock: Clock,
) : ScheduledEventRepository {

    override fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>> =
        eventDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>> =
        eventDao.getByType(type.name).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): ScheduledEvent? = eventDao.getByIdOnce(id)?.toDomain()

    override suspend fun getMaxPosition(date: String): Int = eventDao.getMaxPosition(date)

    override suspend fun add(event: ScheduledEvent): Long {
        // Метки времени ставятся только там, где их ещё нет: событиям из
        // синхронизации значения приходят с сервера и не перетираются.
        val now = clock.millis()
        // При единственной подписке событие рождается её событием (этап 2):
        // dirty сразу — иначе первое «кольцо» потребует лишней правки.
        val accountId = CaldavAttribution.targetFor(event.type, event.caldavAccountId, caldavDao.getAll())
        val id = eventDao.insert(
            event.toEntity().copy(
                createdAt = event.createdAt ?: now,
                modifiedAt = event.modifiedAt ?: now,
                caldavAccountId = accountId,
                dirty = accountId != null,
            ),
        )
        if (accountId != null) syncScheduler.requestPush()
        return id
    }

    override suspend fun update(event: ScheduledEvent) {
        // Правка двигает момент изменения — основу разрешения
        // конфликтов («последняя правка побеждает»).
        var entity = event.toEntity().copy(modifiedAt = clock.millis())
        val existing = eventDao.getByIdOnce(event.id)
        // В редакторе нет поля даты: в снапшоте лежит день, в котором открыт
        // диалог. Для дней рождения и повторяющихся событий UI показывает
        // развёрнутые вхождения, поэтому записи всегда возвращаем сохранённую
        // якорную дату — иначе конвертация «день рождения → обычное» молча
        // затирала год рождения годом просматриваемого вхождения.
        // Для «Выходного» правило обратное: он помечает конкретный открытый
        // день, поэтому берётся дата из снапшота.
        if (event.type != ScheduledEventType.WEEKEND) {
            existing?.let {
                entity = entity.copy(
                    eventDate = it.eventDate,
                    // Дата создания не «стареет» от правки; у строк старых
                    // версий она неизвестна — фиксируем момент первой
                    // известной правки.
                    createdAt = entity.createdAt ?: it.createdAt,
                )
            }
        }
        // Этап 2: правка приписанного события идёт в очередь отправки;
        // ранее локальное событие при единственной подписке приписывается ей.
        val accountId = CaldavAttribution.targetFor(
            event.type,
            existing?.caldavAccountId,
            caldavDao.getAll(),
        )
        val dirty = CaldavAttribution.isDirty(event.type, existing?.caldavAccountId, accountId)
        if (accountId != null && entity.caldavAccountId == null) {
            entity = entity.copy(caldavAccountId = accountId)
        }
        if (dirty) entity = entity.copy(dirty = true)
        existing?.takeIf { it.caldavAccountId != null }?.let { row ->
            // ETag/href переживают правку: без них PUT уходил бы как «первая
            // публикация» (If-None-Match:*), а сервер, где объект уже есть,
            // отвечал бы 412 на каждое сохранение.
            entity = entity.copy(etag = row.etag, serverHref = row.serverHref)
        }
        existing?.let { row ->
            // Происхождение переживает правку: снапшот редактора флага не
            // несёт, «приехало ли содержимое с сервера» — свойство строки.
            // Без перезаписи здесь сохранившееся значение стёрлось бы
            // (событие из вычитки после первой правки гасило бы бейдж).
            entity = entity.copy(remoteImported = row.remoteImported)
        }
        eventDao.update(entity)
        if (dirty) syncScheduler.requestPush()
    }

    override suspend fun delete(event: ScheduledEvent) {
        // Tombstone пишется вместе со строкой, одной транзакцией: вычитка,
        // случившаяся посередине, увидела бы удалённый UID и ввернула бы
        // объект обратно — а очередь DELETE ещё не доехала.
        val row = eventDao.getByIdOnce(event.id)
        val tombstone = row?.toTombstone(clock.millis())
        tx.inTransaction {
            tombstone?.let { tombstoneDao.insert(it) }
            row?.let { eventDao.delete(it) }
        }
        if (tombstone != null) syncScheduler.requestPush()
    }
}
