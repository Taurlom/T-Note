package ru.taurlom.tnote.data.sync

import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ScheduledEvent
import java.time.Clock
import java.time.LocalDate
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
 * нашу правку / строка технически не отправляема (битая дата) /
 * временный сбой сервера — очередь ждёт следующего прогона.
 */
private enum class PushOutcome { Pushed, Overwritten, Skipped, Deferred }

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
 * Бэкфилл маркера: события с пользовательской отделкой (иконка/цвет/
 * «скрыть прошлое»), отправленные старыми версиями без маркера, при
 * вычитке встают в очередь отправки — PUT добавляет DESCRIPTION-маркер,
 * и отделка начинает ездить между устройствами через серверы без
 * X-свойств (needsMarkerBackfill).
 *
 * Заметки дня едут тем же обменом событиями-носителями (NoteIcsMapper):
 * имя «✍️ Заметка», текст в DESCRIPTION, UID детерминированный от даты
 * (заметка одна на день). В вычитке они расходятся по таблице
 * calendar_notes, минуя события; пропавшие на сервере заметки удаляются
 * той же разницей, dirty-заметки — очередь PUT с тем же LWW.
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
 *
 * Капризы Яндекса из живых проб (ноябрь 2026): невалидное по RFC тело —
 * дубль DTSTAMP от ical4j — сервер отвечает 504-м и не создаёт; If-None-
 * Match:* на первую публикацию игнорируется (второй PUT того же UID тоже
 * 201, объект молча перезаписывается); ETag приходит без кавычек, а
 * If-Match признаётся только в цитированной форме — нормализация на
 * границе транспорта (normalizeEtag). Все неизвестные X-свойства при PUT
 * выбрасываются, включая X-TNOTE-*: иконка/цвет/«скрыть прошлое» едут
 * маркером в DESCRIPTION (tnoteDescriptionMarker), а при отсутствии и
 * маркера берутся при переписи из локальной строки (applyRemote). Тела text/calendar
 * уходят с UTF-8 BOM: без него сервер декодирует их как cp1252 и хранит
 * кракозябры (charset=... он игнорирует во всех формах), а имена, уже
 * испорченные таким декодированием (чужие клиенты без BOM, старые наши
 * PUT-ы), чинятся при разборе (repairMojibake). Временные сбои (5xx) не
 * рвут прогон: строка остаётся в очереди, DELETE tombstones всё равно
 * доходят, в настройках остаётся след незавершённого прогона.
 */
@Singleton
class CaldavSyncEngine @Inject constructor(
    private val eventDao: ScheduledEventDao,
    private val noteDao: CalendarNoteDao,
    private val caldavDao: CaldavDao,
    private val tombstoneDao: CaldavTombstoneDao,
    private val api: CaldavApi,
    private val tx: SyncTransaction,
    private val clock: Clock,
) {

    suspend fun sync(account: CaldavAccountEntity, password: String, api: CaldavApi? = null): CaldavSyncResult {
        // Транспорт выбирается на прогон: Яндекс — CaldavClient, Google —
        // GoogleCalendarApi (оба реализуют [CaldavApi]); по умолчанию —
        // транспорт из конструктора (юнит-тесты подставляют фейк так же).
        val transport = api ?: this.api
        val pulled = transport.pullEvents(account.collectionUrl, account.username, password)

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
                                // UID впервые увиден вычиткой — событие чужое:
                                // это бейдж источника (Яндекс/Google) (собственные
                                // события появляется в базе раньше вычитки,
                                // они видят флаг из строки, не с нуля).
                                remoteImported = true,
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
                            // Бэкфилл маркера — той же очередью отправки:
                            // PUT уносит текущее состояние с маркером.
                            if (existing.caldavAccountId == account.id && needsMarkerBackfill(existing, remote)) {
                                eventDao.markDirty(existing.id)
                            }
                            updated++
                        }
                }
            }
            // Заметки-носители («✍️ …») идут своей таблицей: та же разница
            // и тот же LWW, ключ — детерминированный UID от даты.
            for (remoteNote in pulled.notes) {
                if (tombstones.containsKey(remoteNote.event.uid)) continue

                when (val existing = noteDao.getByDateOnce(remoteNote.event.date)) {
                    null -> {
                        noteDao.insert(remoteNote.toNoteEntity(account.id))
                        added++
                    }

                    else ->
                        if (existing.dirty) {
                            if (remoteNote.event.modifiedAt.orZero() > existing.modifiedAt.orZero()) {
                                noteDao.insert(applyNoteRemote(existing, remoteNote, account))
                                overwritten++
                            }
                        } else {
                            noteDao.insert(applyNoteRemote(existing, remoteNote, account))
                            updated++
                        }
                }
            }
            removed = eventDao.deleteNotInUids(account.id, pulled.events.map { it.event.uid }) +
                noteDao.deleteNotInDates(account.id, pulled.notes.map { it.event.date })
        }

        // ── Push правок: сеть по строке, запись — короткая транзакция ──
        var deferred = 0
        for (row in eventDao.getDirtyFor(account.id)) {
            when (pushRow(account, row, password, transport)) {
                PushOutcome.Pushed -> pushed++

                PushOutcome.Overwritten -> overwritten++

                PushOutcome.Skipped -> Unit

                // нечему отправляться — очередь ждёт
                PushOutcome.Deferred -> deferred++ // временный сбой — до следующего прогона
            }
        }
        for (row in noteDao.getDirtyFor(account.id)) {
            when (pushNoteRow(account, row, password, transport)) {
                PushOutcome.Pushed -> pushed++
                PushOutcome.Overwritten -> overwritten++
                PushOutcome.Skipped -> Unit
                PushOutcome.Deferred -> deferred++
            }
        }

        // ── Push удалений: tombstones подтверждаются сервером ──
        for (tombstone in tombstoneDao.getAllFor(account.id)) {
            val href = tombstone.href ?: transport.objectHref(account.collectionUrl, tombstone.uid)
            val code = transport.deleteEvent(href, account.username, password)
            when (code) {
                in 200..299, 404 -> {
                    // 204 — удалено; 404 — «и так нет»: очистка очереди
                    // легитимна в обоих случаях.
                    tx.inTransaction { tombstoneDao.deleteByUid(tombstone.uid) }
                    pushed++
                }

                else -> deferred++ // временный сбой: строка удаления доживёт до следующего прогона
            }
        }

        // Прогон с невыполненной частью — не успех: время последней вычитки
        // обновляем, но в настройках остаётся причина незавершённого прогона.
        if (deferred > 0) {
            caldavDao.markSynced(account.id, clock.millis())
            caldavDao.markFailed(account.id, "Не отправлено: $deferred (сбой сервера, повторится)")
        } else {
            caldavDao.markSynced(account.id, clock.millis())
        }
        return CaldavSyncResult(added, updated, removed, pushed, overwritten)
    }

    /**
     * Нужен ли бэкфилл маркера для строки, переписанной сервером. Серверная
     * копия без наших метаданных (X-* Яндекс выбросил, DESCRIPTION-маркера
     * ещё нет — объект последний раз писал старый PUT) и без description
     * (добавление маркера ничего пользовательского не сотрёт); у локальной
     * строки при этом есть своя отделка — иконка/цвет/«скрыть прошлое» не
     * дефолтные, есть что донести до других устройств.
     *
     * Верно и обратное: у импортированного события с дефолтной отделкой
     * синхронизировать нечего, а его перезапись только потеряла бы форму
     * веб-Яндекса (ссылку события, цвет и категории их веб-формы). Чистые
     * строки всех устройств и так согласованы (перепись сервером), так что
     * бэкфилл непустой отделки не спорит с параллельной правкой: конфликт
     * разрешает обычный LWW. Строка ставится в очередь отправки: PUT
     * уносит маркер, и следующая вычитка уже видит метаданные
     * (metadataFromIcs) — бэкфилл сойдётся за один прогон.
     */
    private fun needsMarkerBackfill(existing: ScheduledEventEntity, remote: CaldavRemoteEvent): Boolean =
        !remote.metadataFromIcs && !remote.hasDescription && existing.hasCustomPresentation()

    /** Отделка строки отличается от дефолтной: её выбирал пользователь. */
    private fun ScheduledEventEntity.hasCustomPresentation(): Boolean =
        icon != "NOTE" || colorArgb != ScheduledEvent.DEFAULT_COLOR || hidePast

    /** Перепись строки сервером целиком: идентификаторы и позиция — свои. */
    private suspend fun applyRemote(existing: ScheduledEventEntity, remote: CaldavRemoteEvent, account: CaldavAccountEntity) {
        // Яндекс выбрасывает все неизвестные X-свойства (проба 2026-11):
        // его копия собственного события приходит без X-TNOTE-*, и слепая
        // перепись стёрла бы иконку/цвет/«скрыть прошлое» уже следующей
        // вычиткой. Эти три поля едут маркером в DESCRIPTION
        // (tnoteDescriptionMarker) — его разбор уже в toScheduledEvent,
        // и «метаданные есть» означает, что серверную копию переписываем
        // целиком. Без маркера и X-* (чужое событие) иконка/цвет/«скрыть
        // прошлое» — локальный выбор пользователя, а не серверные данные:
        // сохраняем их. Полноценный CalDAV-сервер X-* возвращает — его
        // копия (metadataFromIcs) переписывает целиком, как раньше.
        val event = if (remote.metadataFromIcs) {
            remote.event
        } else {
            val local = existing.toDomain()
            remote.event.copy(
                icon = local.icon,
                colorArgb = local.colorArgb,
                hidePastOccurrences = local.hidePastOccurrences,
            )
        }
        eventDao.update(
            event.toEntity().copy(
                id = existing.id,
                caldavAccountId = existing.caldavAccountId ?: account.id,
                // Происхождение переписью не трогается: вычитка эхом
                // возвращает и собственные события (протолкнутые в
                // подписку), сплошной флаг гасил бы их бейдж. Флаг ставится
                // один раз — когда UID впервые попадает в базу вычиткой.
                remoteImported = existing.remoteImported,
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
    private suspend fun pushRow(
        account: CaldavAccountEntity,
        row: ScheduledEventEntity,
        password: String,
        transport: CaldavApi,
    ): PushOutcome {
        val event = row.toDomain()
        // Битая дата не разберётся в VEVENT: строка остаётся в очереди
        // вместо вечного ретрая.
        if (event.dateOrNull() == null) return PushOutcome.Skipped

        val href = row.serverHref ?: transport.objectHref(account.collectionUrl, row.uid)
        val ics = eventCalendar(listOf(event.toIcsEvent())).icsText()

        val first = transport.putEvent(
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
        when (first.code) {
            412, 404 -> Unit

            // конфликт — разрешаем ниже по потоку

            // Прочие коды — временный сбой сервера: рвать весь прогон из-за
            // одной строки нельзя, застрявшая в throw-месте очередь
            // блокировала бы и DELETE tombstones на каждом прогоне.
            else -> return PushOutcome.Deferred
        }

        // Конфликт: серверный объект уже не тот (412) или исчез (404).
        val fresh = transport.pullEvent(href, account.username, password)
        if (fresh == null) {
            // Объект исчез между вычиткой и отправкой: правка важнее —
            // публикуем заново без предусловий.
            val recreate = transport.putEvent(
                url = href,
                ics = ics,
                username = account.username,
                password = password,
                etag = null,
                createIfAbsent = true,
            )
            if (recreate.code !in 200..299) {
                return PushOutcome.Deferred
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
        val retry = transport.putEvent(
            url = href,
            ics = ics,
            username = account.username,
            password = password,
            etag = fresh.etag,
            createIfAbsent = false,
        )
        if (retry.code !in 200..299) {
            return PushOutcome.Deferred
        }
        eventDao.markPushed(row.id, retry.etag, href)
        return PushOutcome.Pushed
    }

    /** null-минусы старых версий: метка неизвестна — младше любой известной. */
    private fun Long?.orZero(): Long = this ?: 0L

    /** Перепись заметки сервером: ключ (день) и момент создания — свои. */
    private fun applyNoteRemote(
        existing: CalendarNoteEntity,
        remote: CaldavRemoteEvent,
        account: CaldavAccountEntity,
    ): CalendarNoteEntity = CalendarNoteEntity(
        eventDate = existing.eventDate,
        // DESCRIPTION сервер мог не донести — текст не выдумываем.
        text = remote.noteText ?: existing.text,
        createdAt = existing.createdAt.takeIf { it > 0 } ?: remote.event.createdAt.orZero(),
        modifiedAt = remote.event.modifiedAt.orZero(),
        caldavAccountId = existing.caldavAccountId ?: account.id,
        // Строка переписана сервером — локальной правки нет: очередь чиста,
        // ETag и href — от свежей вычитки.
        dirty = false,
        etag = remote.etag,
        serverHref = remote.href,
    )

    /** Первая вычитка заметки: строка рождается серверной копией. */
    private fun CaldavRemoteEvent.toNoteEntity(accountId: Long): CalendarNoteEntity = CalendarNoteEntity(
        eventDate = event.date,
        text = noteText.orEmpty(),
        createdAt = event.createdAt.orZero(),
        modifiedAt = event.modifiedAt.orZero(),
        caldavAccountId = accountId,
        dirty = false,
        etag = etag,
        serverHref = href,
    )

    /**
     * Доведение dirty-заметки до сервера — зеркало [pushRow] с теми же
     * предусловиями и LWW; объект-носитель собирается из заметки
     * (NoteIcsMapper), ключ на сервере — детерминированный UID от даты.
     */
    private suspend fun pushNoteRow(
        account: CaldavAccountEntity,
        row: CalendarNoteEntity,
        password: String,
        transport: CaldavApi,
    ): PushOutcome {
        val note = row.toDomain()
        val anchor = runCatching { LocalDate.parse(note.date) }.getOrNull()
            ?: return PushOutcome.Skipped

        val href = row.serverHref ?: transport.objectHref(account.collectionUrl, noteUidFor(note.date))
        val ics = eventCalendar(listOf(note.toIcsEvent())).icsText()

        val first = transport.putEvent(
            url = href,
            ics = ics,
            username = account.username,
            password = password,
            etag = row.etag,
            createIfAbsent = row.serverHref == null,
        )
        if (first.code in 200..299) {
            noteDao.markPushed(row.eventDate, first.etag, href)
            return PushOutcome.Pushed
        }
        when (first.code) {
            412, 404 -> Unit
            else -> return PushOutcome.Deferred
        }

        // Конфликт: серверный объект уже не тот (412) или исчез (404).
        val fresh = transport.pullEvent(href, account.username, password)
        if (fresh == null) {
            val recreate = transport.putEvent(
                url = href,
                ics = ics,
                username = account.username,
                password = password,
                etag = null,
                createIfAbsent = true,
            )
            if (recreate.code !in 200..299) {
                return PushOutcome.Deferred
            }
            noteDao.markPushed(row.eventDate, recreate.etag, href)
            return PushOutcome.Pushed
        }
        if (fresh.event.modifiedAt.orZero() > row.modifiedAt.orZero()) {
            noteDao.insert(applyNoteRemote(row, fresh, account))
            return PushOutcome.Overwritten
        }
        val retry = transport.putEvent(
            url = href,
            ics = ics,
            username = account.username,
            password = password,
            etag = fresh.etag,
            createIfAbsent = false,
        )
        if (retry.code !in 200..299) {
            return PushOutcome.Deferred
        }
        noteDao.markPushed(row.eventDate, retry.etag, href)
        return PushOutcome.Pushed
    }
}
