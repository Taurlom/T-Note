package ru.taurlom.tnote.data.sync

import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.CaldavSyncException
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
 * Итог отправки одной dirty-строки: доехала до сервера / сервер перезаписал
 * нашу правку / строка технически не отправляема (битая дата, WEEKEND).
 */
private enum class PushOutcome { Pushed, Overwritten, Skipped }

/**
 * Движок синхронизации одного аккаунта: вычитка (calendar-query) и
 * отправка локальных изменений (этап 2). Ключ сопоставления — UID.
 *
 * Pull — разница против сервера:
 *  - событие есть на сервере и его нет у нас → insert (позиция — «максимум
 *    позиций этого дня» + 1). UID с tombstone не вписывается: пользователь
 *    удалил это событие здесь, DELETE ещё в очереди;
 *  - есть и там, и там без dirty → перепись целиком: сервер источник
 *    истины, локальных правок у строки нет;
 *  - есть и там, и там с dirty → last-write-wins по моменту правки
 *    (у нас — modifiedAt сохранения; у сервера — LAST-MODIFIED/DTSTAMP).
 *    Сервер новее → перепись + счётчик overwritten (пользователь видит
 *    снек «не молча»); локальное новее → строка ждёт push, его ей заберёт;
 *  - было импортировано этим аккаунтом, а на сервере исчезло: чистые
 *    строки удаляются, dirty — остаются (их PUT воскресит объект;
 *    правка пользователя важнее, чем неизвестный удалитель).
 *
 * Push — очередь локальных изменений:
 *  - dirty-строки аккаунта → PUT по serverHref (или «<uid>.ics», если
 *    объект ещё не гулял по серверу), If-Match по ETag из вычитки /
 *    If-None-Match:* для первой публикации. 412 — точечная перечитка
 *    объекта и LWW заново: наша правка новее → PUT с живым ETag, серверная
 *    новее → примятие серверной версии (overwritten). 404 — объект исчез:
 *    публикуем заново;
 *  - tombstones → DELETE; 204/404 → строка очереди стёрта.
 *
 * Сеть всегда вне транзакции Room: вычитка до неё, применение — короткая
 * транзакция, каждая запись push'а — своя. Полная вычитка вместо
 * sync-token выбрана осознанно: Яндекс REPORT sync-collection не
 * поддерживает (проверено), календарь личный и небольшой. Локальные
 * события (caldavAccountId == null) движок не трогает — приписку делает
 * репозиторий (см. CaldavAttribution).
 */
@Singleton
class CaldavSyncEngine @Inject constructor(
    private val eventDao: ScheduledEventDao,
    private val caldavDao: CaldavDao,
    private val tombstoneDao: CaldavTombstoneDao,
    private val api: CaldavApi,
    private val tx: SyncTransaction,
    private val clock: Clock,
) {

    suspend fun sync(account: CaldavAccountEntity, password: String): CaldavSyncResult {
        val pulled = api.pullEvents(account.collectionUrl, account.username, password)

        var added = 0
        var updated = 0
        var removed = 0
        var pushed = 0
        var overwritten = 0

        // ── Pull: короткая транзакция на применение разницы ──
        tx.inTransaction {
            val tombstones = tombstoneDao.getAllFor(account.id).associateBy { it.uid }
            for (remote in pulled.events) {
                // Пользователь удалил событие здесь: пусть DELETE в очереди
                // доехал сервером, а не воскрешался каждую вычитку.
                if (tombstones.containsKey(remote.event.uid)) continue

                when (val existing = eventDao.getByUid(remote.event.uid)) {
                    null -> {
                        val position = eventDao.getMaxPosition(remote.event.date) + 1
                        eventDao.insert(
                            remote.event.toEntity().copy(
                                id = 0,
                                caldavAccountId = account.id,
                                position = position,
                                dirty = false,
                                etag = remote.etag,
                                serverHref = remote.href,
                            ),
                        )
                        added++
                    }

                    else ->
                        if (existing.dirty) {
                            // Локальная правка ещё не доехала: решает LWW.
                            if (remote.event.modifiedAt.orZero() > existing.modifiedAt.orZero()) {
                                applyRemote(existing, remote, account)
                                overwritten++
                            }
                            // Иначе — строка остаётся на отправку push'ем.
                        } else {
                            applyRemote(existing, remote, account)
                            updated++
                        }
                }
            }
            removed = eventDao.deleteNotInUids(account.id, pulled.events.map { it.event.uid })
        }

        // ── Push правок: сеть по строке, запись — короткая транзакция ──
        for (row in eventDao.getDirtyFor(account.id)) {
            when (pushRow(account, row, password)) {
                PushOutcome.Pushed -> pushed++
                PushOutcome.Overwritten -> overwritten++
                PushOutcome.Skipped -> Unit // нечему отправляться — очередь ждёт
            }
        }

        // ── Push удалений: tombstones подтверждаются сервером ──
        for (tombstone in tombstoneDao.getAllFor(account.id)) {
            val href = tombstone.href ?: api.objectHref(account.collectionUrl, tombstone.uid)
            val code = api.deleteEvent(href, account.username, password)
            when (code) {
                in 200..299, 404 -> {
                    // 204 — удалено; 404 — «и так нет»: очистка очереди
                    // легитимна в обоих случаях.
                    tx.inTransaction { tombstoneDao.deleteByUid(tombstone.uid) }
                    pushed++
                }

                else -> throw CaldavSyncException.Server("DELETE отклонён: HTTP $code")
            }
        }

        caldavDao.markSynced(account.id, clock.millis())
        return CaldavSyncResult(added, updated, removed, pushed, overwritten)
    }

    /** Перепись строки сервером целиком: идентификаторы и позиция — свои. */
    private suspend fun applyRemote(existing: ScheduledEventEntity, remote: CaldavRemoteEvent, account: CaldavAccountEntity) {
        eventDao.update(
            remote.event.toEntity().copy(
                id = existing.id,
                caldavAccountId = existing.caldavAccountId ?: account.id,
                position = existing.position,
                // Строка переписана сервером — её локальной правки нет:
                // очередь чиста, ETag и href — от свежей вычитки.
                dirty = false,
                etag = remote.etag,
                serverHref = remote.href,
            ),
        )
    }

    /**
     * Доведение одной dirty-строки до сервера; применяется одна попытка
     * разрешения конфликта — упорный 412 оставит строку dirty до следующей
     * синхронизации. Сетевые вызовы вне транзакций Room.
     */
    private suspend fun pushRow(account: CaldavAccountEntity, row: ScheduledEventEntity, password: String): PushOutcome {
        val event = row.toDomain()
        // Ничего не выражаемое наружу или гонимое прочь: «выходной» —
        // генерируемая отметка (границы v1), битая дата не разберётся в
        // VEVENT. Строки остаются в очереди вместо вечного ретрая.
        if (event.type == ScheduledEventType.WEEKEND) return PushOutcome.Skipped
        if (event.dateOrNull() == null) return PushOutcome.Skipped

        val href = row.serverHref ?: api.objectHref(account.collectionUrl, row.uid)
        val ics = eventCalendar(listOf(event.toIcsEvent())).icsText()

        val first = api.putEvent(
            url = href,
            ics = ics,
            username = account.username,
            password = password,
            etag = row.etag,
            createIfAbsent = row.serverHref == null,
        )
        if (first.code in 200..299) {
            eventDao.markPushed(row.id, first.etag, href)
            return PushOutcome.Pushed
        }
        if (first.code != 412 && first.code != 404) {
            throw CaldavSyncException.Server("PUT отклонён: HTTP ${first.code}")
        }

        // Конфликт: серверный объект уже не тот (412) или исчез (404).
        val fresh = api.pullEvent(href, account.username, password)
        if (fresh == null) {
            // Объект исчез между вычиткой и отправкой: правка важнее —
            // публикуем заново без предусловий.
            val recreate = api.putEvent(
                url = href,
                ics = ics,
                username = account.username,
                password = password,
                etag = null,
                createIfAbsent = true,
            )
            if (recreate.code !in 200..299) {
                throw CaldavSyncException.Server("PUT отклонён: HTTP ${recreate.code}")
            }
            eventDao.markPushed(row.id, recreate.etag, href)
            return PushOutcome.Pushed
        }
        if (fresh.event.modifiedAt.orZero() > row.modifiedAt.orZero()) {
            // Серверная правка новее — наша уступает молча-но-не-молча:
            // строка переписывается сервером, пользователь увидит снек.
            applyRemote(row, fresh, account)
            return PushOutcome.Overwritten
        }
        // Наша правка новее: PUT с живым ETag от свежей перечитки.
        val retry = api.putEvent(
            url = href,
            ics = ics,
            username = account.username,
            password = password,
            etag = fresh.etag,
            createIfAbsent = false,
        )
        if (retry.code !in 200..299) {
            throw CaldavSyncException.Server("PUT отклонён: HTTP ${retry.code}")
        }
        eventDao.markPushed(row.id, retry.etag, href)
        return PushOutcome.Pushed
    }

    /** null-минусы старых версий: метка неизвестна — младше любой известной. */
    private fun Long?.orZero(): Long = this ?: 0L
}
