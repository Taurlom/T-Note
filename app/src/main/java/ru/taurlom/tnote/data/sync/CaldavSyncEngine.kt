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
import ru.taurlom.tnote.data.security.httpsOriginOf
import ru.taurlom.tnote.data.security.originOf
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.CaldavSyncException
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
 * временный сбой сервера или нечитаемая перечитка конфликтного объекта —
 * очередь ждёт следующего прогона / строка правилась в сетевой ход и её
 * версия на сервер ещё не уезжала (очередь осталась, PUT повторится) /
 * серверный объект нельзя пересобирать из нашей модели (в нём есть данные
 * вне модели или исключения серии) — PUT стёр бы их, поэтому правка
 * остаётся локальной и ждёт, пока объект станет простой.
 */
private enum class PushOutcome { Pushed, Overwritten, Skipped, Deferred, Stale, Protected, Rejected }

/** Доехала ли правка до сервера этим прогоном (или сервер принял свою копию). */
private val PushOutcome.reachedServer: Boolean
    get() = when (this) {
        PushOutcome.Pushed, PushOutcome.Overwritten, PushOutcome.Stale -> true
        PushOutcome.Skipped, PushOutcome.Deferred, PushOutcome.Protected, PushOutcome.Rejected -> false
    }

/**
 * Движок синхронизации одного аккаунта: вычитка (calendar-query) и
 * отправка локальных изменений (этап 2). Ключ сопоставления —
 * (аккаунт, UID): один и тот же UID на двух серверах — две разные
 * идентичности, каждая живёт своей строкой в базе.
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
 *    снек «не молча»); локальное новее → строка ждёт push, его ей заберёт,
 *    но серверная привязка (href/ETag) освежается вычиткой отдельно
 *    (rememberServerState): PUT пойдёт по адресу сервера, а не по
 *    выведенному, — иначе после восстановления бэкапа CalDAV получил бы
 *    второй ресурс с тем же UID;
 *  - было импортировано этим аккаунтом, а на сервере исчезло: чистые
 *    строки удаляются, dirty — остаются (их PUT воскресит объект;
 *    правка пользователя важнее, чем неизвестный удалитель). Воскрешение
 *    — явный конфликт, а не молчаливое следствие: такие строки считаются
 *    в [CaldavSyncResult.resurrected] и доносятся снеком;
 *
 * Удаления по разнице — только на полном снапшоте ([CaldavPull.complete]).
 * Нечитаемая запись вычитки (тело без calendar-data, битый ICS, VEVENT без
 * UID/DTSTART, событие Google без id/даты) для списка «чего на сервере нет»
 * неотличима от удалённой, и стирать по такому списку локальные строки —
 * терять данные: вставки и переписи безвредны, а разницу «на удаление»
 * догонит следующая полная вычитка. Ответ, не похожий на календарный
 * (не 207 multistatus, не calendar#events), транспорт возвращает отказом
 * всего прогона — до применения разницы дело вообще не доходит.
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
 * calendar_notes, минуя события; фактический UID серверного носителя —
 * он может отличаться от производного (объект создан в веб-форме или
 * чужим клиентом) — запоминается в строке (remoteUid) и по нему же
 * строятся очередь удалений и подавление вычитки. Пропавшие на сервере
 * заметки удаляются той же разницей, dirty-заметки — очередь PUT с тем
 * же LWW.
 *
 * Push — очередь локальных изменений:
 *  - dirty-строки аккаунта → PUT по serverHref (или «<uid>.ics», если
 *    объект ещё не гулял по серверу), If-Match по ETag из вычитки /
 *    If-None-Match:* для первой публикации. Принятая запись без ETag
 *    в ответе — объект перечитывается сразу (refetchEtag): следующий PUT
 *    идёт со свежим предусловием, а не вслепую. 412 — точечная перечитка
 *    объекта и LWW заново: наша правка новее → PUT с живым ETag, серверная
 *    новее → примятие серверной версии (overwritten). 404 — объект исчез:
 *    публикуем заново; перечитка, которая не разобралась, — не «объекта
 *    нет»: строка остаётся в очереди, PUT поверх живой серверной копии не
 *    уходит;
 *  - tombstones → условный DELETE по сохранённому ETag (If-Match);
 *    204/404 → строка очереди стёрта. 412 — объект правился после нашего
 *    удаления: точечная перечитка и last-write-wins по моменту — серверная
 *    правка новее deletedAt tombstone → удаление уступает,
 *    объект возвращается в базу (счётчик restored + снек), иначе — DELETE
 *    безусловно. Без сохранённого ETag удаление остаётся безусловным:
 *    сервер без ETag CAS не даёт, молча-новое не гарантировано.
 *    Исключение — идентичность, которую этот же прогон уже опубликовал
 *    PUT'ом:
 *    tombstone устарел (объект пересоздан локально под тем же UID), очередь
 *    снимается без сетевого хода. Заметки — самый частый случай: её UID
 *    выводится из даты, поэтому «удалить день и написать заново» — одна
 *    идентичность, а безусловный DELETE снёс бы только что вписанный объект,
 *    следом вычитка вычистила бы и локальную строку.
 *
 * PUT пересобирает ресурс из модели T-Note одним VEVENT, поэтому серверные
 * объекты с данными вне модели (место, участники, напоминания, чужое
 * DESCRIPTION, невыразимый RRULE, исключения серии рядом с мастером) движок
 * не отправляет вовсе — иначе правка пользователя стёрла бы чужие данные.
 * Транспорты помечают такие объекты [CaldavRemoteEvent.notReproducible],
 * их правки остаются локальными и переживают вычитку (строка не снимается
 * из очереди, пока объект не станет простым); причина попадает в карточку
 * аккаунта. Полное решение — хранить исходный ICS/JSON и менять только
 * поля T-Note.
 *
 * Ресурс повторяющейся серии разбирается атомарно: строкой живёт мастер
 * (VEVENT без RECURRENCE-ID), а исключения (overrides) — признак
 * «не перезаписывать». Вписывать их отдельными событиями нельзя: UID у них
 * общий с мастером, и компоненты по очереди затирали бы одну строку в
 * порядке, который диктует сервер. Точечная перечитка идёт тем же алгоритмом.
 *
 * PUT идёт по снапшоту строки, взятому ДО сетевого хода, а сеть не
 * блокирует правки пользователя. Поэтому подтверждение отправки — CAS по
 * счётчику локальных мутаций ([ScheduledEventEntity.localRevision]):
 * очередь снимается только с той версии, которую отправляли. Вписанная в
 * сетевой ход правка остаётся dirty (её версия на сервер ещё не уезжала) и
 * уезжает следующим прогоном; то же правило бережёт конфликтную ветку —
 * серверная копия не перетирает строку, изменившуюся пока мы спрашивали
 * сервер. По [ScheduledEvent.modifiedAt] так нельзя: две правки в одну
 * миллисекунду неотличимы.
 *
 * Сеть всегда вне транзакции Room: вычитка целиком собирается в памяти
 * (staging — [CaldavPull]) и проверяется на полноту до открытия транзакции,
 * применение — одна короткая транзакция, каждая запись push'а — своя. Перед
 * применением транзакция сверяет, что аккаунт ещё подключён: вычитка идёт в
 * сети секунды, и за это время подписку могут отключить, — строки с мёртвым
 * accountId не вписываются, прогон сворачивается (координатор — у
 * CaldavRepositoryImpl). Полная вычитка вместо sync-token выбрана осознанно:
 * Яндекс REPORT sync-collection не поддерживает (проверено), календарь личный
 * и небольшой. Локальные события (caldavAccountId == null) движок не трогает —
 * приписку делает репозиторий (см. CaldavAttribution).
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
 * PUT-ы), чинятся при разборе (repairMojibake). Временные сбои (5xx) и
 * постоянные отказы конкретной строке (4xx «invalid») не рвут прогон:
 * строка остаётся в очереди, остальные PUT и DELETE tombstones доходят,
 * в настройках остаётся след незавершённого прогона.
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

        // Временная заморозка перезаписи (см. CaldavRemoteEvent.notReproducible):
        // серверные объекты, которые нельзя пересобрать из нашей модели, —
        // в них есть данные вне модели (место, участники, напоминания, чужое
        // DESCRIPTION, невыразимый RRULE) или исключения серии рядом с
        // мастером. PUT одним VEVENT из модели стёр бы их с сервера, поэтому
        // правки таких строк не уезжают и остаются локальными. Полное
        // решение — хранить исходный ICS/JSON и менять только поля T-Note.
        val protectedUids = mutableSetOf<String>()
        protectedUids += pulled.protectedUids
        (pulled.events + pulled.notes).forEach { remote ->
            if (remote.notReproducible) protectedUids += remote.event.uid
        }
        // Вычитка не успела всех прочесть — про чужие строки мы ничего не
        // знаем, а PUT по ним идёт вслепую: до полной вычитки они тоже
        // заморожены (свои, созданные в приложении, отправляем как обычно).
        val foreignFrozen = !pulled.complete

        var added = 0
        var updated = 0
        var removed = 0
        var pushed = 0
        var overwritten = 0

        // ── Pull: короткая транзакция на применение разницы ──
        // Перед применением — повторная проверка, что подписка ещё жива:
        // вычитка висит в сети секунды, и за это время аккаунт могли
        // отключить (или базу заменили резервной копией, которая гасит все
        // подписки). Строки с мёртвым accountId вписывать нельзя: их больше
        // никто не почистит — diff-удаления и обе очереди привязаны к
        // аккаунту, а attributeAll берёт только строки без приписки. Ключ id
        // автоинкрементный и не переиспользуется, поэтому существования
        // строки достаточно: отдельное «поколение» аккаунта не заводим.
        var alive = true
        tx.inTransaction {
            if (caldavDao.getById(account.id) == null) {
                alive = false
                return@inTransaction
            }
            val tombstones = tombstoneDao.getAllFor(account.id).associateBy { it.uid }
            for (remote in pulled.events) {
                // Пользователь удалил событие здесь: пусть DELETE в очереди
                // доехал сервером, а не воскрешался каждую вычитку.
                if (tombstones.containsKey(remote.event.uid)) continue

                when (val existing = eventDao.getByUid(account.id, remote.event.uid)) {
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
                            } else {
                                // Наша правка новее — строка остаётся на отправку
                                // push'ем со своим содержимым и очередью. Но серверную
                                // идентичность обновляем отдельно: после восстановления
                                // бэкапа локальный href/ETag утеряны или врут, а объект
                                // на сервере может жить по веб-созданному адресу. PUT по
                                // выведенному <uid>.ics поставил бы ДВОЙНИК ресурса с тем
                                // же UID, после чего diff и удаления неоднозначны.
                                eventDao.rememberServerState(existing.id, remote.etag, remote.href)
                            }
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
            // и тот же LWW, ключ — день (UID носителя детерминирован от
            // даты и одинаков у всех подписок, поэтому строку дня чужой
            // подписки вычитка не трогает: иначе href/etag одного сервера
            // ложатся на владельца другого).
            for (remoteNote in pulled.notes) {
                if (tombstones.containsKey(remoteNote.event.uid)) continue

                val existing = noteDao.getByDateOnce(remoteNote.event.date)
                if (existing != null && existing.caldavAccountId != null && existing.caldavAccountId != account.id) continue

                when (existing) {
                    null -> {
                        noteDao.insert(remoteNote.toNoteEntity(account.id))
                        added++
                    }

                    else ->
                        if (existing.dirty) {
                            if (remoteNote.event.modifiedAt.orZero() > existing.modifiedAt.orZero()) {
                                noteDao.insert(applyNoteRemote(existing, remoteNote, account))
                                overwritten++
                            } else {
                                // Зеркало событийной ветки: правка заметки остаётся
                                // в очереди, а серверная привязка (href/ETag
                                // носителя) освежается вычиткой — вместе с
                                // фактическим UID: по нему строит tombstone
                                // удаление и вычитку же подавляет.
                                noteDao.rememberServerState(existing.eventDate, remoteNote.etag, remoteNote.href, remoteNote.event.uid)
                            }
                        } else {
                            noteDao.insert(applyNoteRemote(existing, remoteNote, account))
                            updated++
                        }
                }
            }
            removed = if (pulled.complete) {
                eventDao.deleteNotInUids(account.id, pulled.events.map { it.event.uid }) +
                    noteDao.deleteNotInDates(account.id, pulled.notes.map { it.event.date })
            } else {
                // Неполный снапшот: «нет в списке» здесь значит «мы не
                // прочитали», а не «сервер удалил». Вставки и переписи выше
                // безвредны, а удаления доживут до полной вычитки — причину
                // пишем в карточку аккаунта после прогона.
                0
            }
        }
        if (!alive) {
            // Подписки больше нет: прогон сворачивается целиком. Ни PUT, ни
            // DELETE, ни отметка о синхронизации — всё это ходы от имени
            // отключённого аккаунта, а строки вычитки уже не вписаны.
            return CaldavSyncResult(0, 0, 0)
        }

        // ── Push правок: сеть по строке, запись — короткая транзакция ──
        var deferred = 0
        // Stale — строка правилась в сетевой ход: её версия на сервер ещё не
        // уехала, очередь осталась, прогон помечается незавершённым.
        var stale = 0
        // Замороженные правки: серверный объект нельзя пересобирать из модели.
        var protectedRows = 0
        // Локальные удаления, уступившие более свежей серверной правке:
        // объект возвращён в базу из конфликтной перечитки (счёт ведёт цикл
        // удалений).
        var restored = 0
        // Разница по удалению намеренно щадит dirty-строки: если опубликованная
        // строка пропала из серверного списка, её PUT воскресит объект —
        // продуктовая политика «правка пользователя важнее неизвестного
        // удалителя» остаётся. Но явной: состоявшееся воскрешение считается
        // в resurrected и доносится снеком, молча это не проходит. На неполной
        // вычитке «нет в списке» значит «мы не прочли», поэтому не отмечаем.
        var resurrected = 0
        // Постоянные отказы сервера конкретным строкам (HTTP 4xx «invalid»):
        // строку не бросаем и очередь не рвём — остальные события и удаления
        // должны доехать этим же прогоном, а причина остаётся на карточке.
        var rejected = 0
        val rejectionReasons = mutableListOf<String>()
        fun recordRejection(error: CaldavSyncException.RowRejected) {
            rejected++
            if (rejectionReasons.size < 3) rejectionReasons += error.message.orEmpty()
        }
        val absentEvents = mutableSetOf<String>()
        val absentNoteDates = mutableSetOf<String>()
        if (pulled.complete) {
            val pulledEventUids = pulled.events.mapTo(mutableSetOf()) { it.event.uid }
            val pulledNoteDates = pulled.notes.mapTo(mutableSetOf()) { it.event.date }
            eventDao.getDirtyFor(account.id).forEach { row ->
                if (row.serverHref != null && row.uid !in pulledEventUids) absentEvents += row.uid
            }
            noteDao.getDirtyFor(account.id).forEach { row ->
                if (row.serverHref != null && row.eventDate !in pulledNoteDates) absentNoteDates += row.eventDate
            }
        }
        // UID'ы, чья правка доехала до сервера этим прогоном (или была им
        // принята): tombstone такой идентичности устарел — ниже он снимается
        // без DELETE, иначе прогон убрал бы объект, который сам же вписал.
        val published = mutableSetOf<String>()
        for (row in eventDao.getDirtyFor(account.id)) {
            val outcome = if (row.uid in protectedUids || (foreignFrozen && row.remoteImported)) {
                PushOutcome.Protected
            } else {
                try {
                    pushRow(account, row, password, transport)
                } catch (error: CaldavSyncException.RowRejected) {
                    recordRejection(error)
                    PushOutcome.Rejected
                }
            }
            // Deferred/Skipped/Protected — на сервере ничего не оказалось:
            // удаление той же идентичности остаётся законным (оно и было
            // намерением).
            if (outcome.reachedServer) {
                published += row.uid
            }
            when (outcome) {
                PushOutcome.Pushed -> {
                    pushed++
                    // PUT ушёл по строке, которой нет на сервере, — локальная
                    // правка воскресила удалённый объект: считаем явно.
                    if (row.uid in absentEvents) resurrected++
                }

                PushOutcome.Overwritten -> overwritten++

                PushOutcome.Skipped -> Unit

                PushOutcome.Stale -> stale++

                PushOutcome.Deferred -> deferred++

                PushOutcome.Protected -> protectedRows++

                // Счёт и причина уже записаны в catch: строка осталась dirty.
                PushOutcome.Rejected -> Unit
            }
        }
        for (row in noteDao.getDirtyFor(account.id)) {
            // Ключ носителя — фактический UID с сервера (remoteUid); у
            // своей, ещё не перечитанной заметки — производный от даты.
            // Те же ключи у tombstones: published снимает очередь именно
            // той идентичности, которую прогон опубликовал (баг 15).
            val carrierUid = row.remoteUid ?: noteUidFor(row.eventDate)
            val outcome = if (carrierUid in protectedUids) {
                PushOutcome.Protected
            } else {
                try {
                    pushNoteRow(account, row, password, transport)
                } catch (error: CaldavSyncException.RowRejected) {
                    recordRejection(error)
                    PushOutcome.Rejected
                }
            }
            if (outcome.reachedServer) {
                published += carrierUid
            }
            when (outcome) {
                PushOutcome.Pushed -> {
                    pushed++
                    if (row.eventDate in absentNoteDates) resurrected++
                }

                PushOutcome.Overwritten -> overwritten++

                PushOutcome.Skipped -> Unit

                PushOutcome.Stale -> stale++

                PushOutcome.Deferred -> deferred++

                PushOutcome.Protected -> protectedRows++

                // Счёт и причина уже записаны в catch: строка осталась dirty.
                PushOutcome.Rejected -> Unit
            }
        }

        // ── Push удалений: tombstones подтверждаются сервером ──
        for (tombstone in tombstoneDao.getAllFor(account.id)) {
            if (tombstone.uid in published) {
                // Одна идентичность не может в одном прогоне уехать и PUT'ом,
                // и DELETE'ом: очередь удаления появилась до того, как её сняло
                // пересоздание (гонка — правка вписалась в сетевой ход, см.
                // CalendarRepositoryImpl.saveNote). Снимаем запись без сетевого
                // хода: свежий серверный объект важнее устаревшего удаления.
                tx.inTransaction { tombstoneDao.deleteForIdentity(tombstone.uid, account.id) }
                continue
            }
            val href = tombstone.href ?: transport.objectHref(account.collectionUrl, tombstone.uid)
            requireAccountOrigin(account, href)
            val code = try {
                transport.deleteEvent(href, account.username, password, tombstone.etag)
            } catch (error: CaldavSyncException.RowRejected) {
                // Постоянный отказ конкретному tombstone: остальные удаления
                // продолжаются, запись остаётся до следующего прогона.
                recordRejection(error)
                continue
            }
            when (code) {
                in 200..299, 404 -> {
                    // 204 — удалено; 404 — «и так нет»: очистка очереди
                    // легитимна в обоих случаях.
                    tx.inTransaction { tombstoneDao.deleteForIdentity(tombstone.uid, account.id) }
                    pushed++
                }

                412 -> {
                    // Сохранённый ETag не совпал: объект правился после
                    // нашего удаления (или его успели пересоздать под тем же
                    // адресом). Решаем тем же last-write-wins, что и правки, —
                    // по моменту tombstone против серверной метки.
                    when (val fresh = transport.pullEvent(href, account.username, password)) {
                        // Пока решали, объект исчез снова: намерение удалено,
                        // очередь закрывается.
                        CaldavObjectResult.NotFound -> {
                            tx.inTransaction { tombstoneDao.deleteForIdentity(tombstone.uid, account.id) }
                            pushed++
                        }

                        is CaldavObjectResult.Unreadable ->
                            deferred++

                        // о серверной копии ничего не известно, ждём следующего прогона

                        is CaldavObjectResult.Found -> {
                            val remote = fresh.event
                            if (remote.event.modifiedAt.orZero() > tombstone.deletedAt) {
                                // Серверная правка новее удаления: удаление
                                // уступает. Объект возвращается в базу, очередь
                                // снимается — явно, со снеком: пользователь
                                // удалял сам и должен увидеть воскрешение.
                                tx.inTransaction {
                                    tombstoneDao.deleteForIdentity(tombstone.uid, account.id)
                                    restoreRemote(remote, account)
                                }
                                restored++
                            } else {
                                // Удаление — последняя операция (или метка
                                // сервера неизвестна): подтверждаем безусловно.
                                val confirm = try {
                                    transport.deleteEvent(href, account.username, password, etag = null)
                                } catch (error: CaldavSyncException.RowRejected) {
                                    recordRejection(error)
                                    continue
                                }
                                when (confirm) {
                                    in 200..299, 404 -> {
                                        tx.inTransaction { tombstoneDao.deleteForIdentity(tombstone.uid, account.id) }
                                        pushed++
                                    }

                                    else -> deferred++
                                }
                            }
                        }
                    }
                }

                else -> deferred++ // временный сбой: строка удаления доживёт до следующего прогона
            }
        }

        // Прогон с невыполненной частью — не успех: время последней вычитки
        // обновляем, но в настройках остаётся причина незавершённого прогона.
        val reasons = buildList {
            if (!pulled.complete) {
                add("Вычитка неполна: ${pulled.unreadable} объектов не разобрались — удаления пропущены")
            }
            if (deferred > 0) {
                add("Не отправлено: $deferred (сбой сервера, повторится)")
            }
            if (stale > 0) {
                add("Изменено во время отправки: $stale (повторится)")
            }
            if (protectedRows > 0) {
                add("Без отправки: $protectedRows (данные сервера вне модели — правки только локально)")
            }
            if (rejected > 0) {
                val detail = rejectionReasons.firstOrNull()?.let { " — $it" }.orEmpty()
                add("Отклонено сервером: $rejected (осталось в очереди)$detail")
            }
        }
        caldavDao.markSynced(account.id, clock.millis())
        if (reasons.isNotEmpty()) {
            caldavDao.markFailed(account.id, reasons.joinToString("; "))
        }
        return CaldavSyncResult(added, updated, removed, pushed, overwritten, restored, resurrected)
    }

    /**
     * Адрес объекта из базы обязан принадлежать серверу аккаунта: подменённая
     * ссылка (импорт копии, правка БД) унесла бы PUT/DELETE/точечную вычитку
     * с паролем на чужой хост. Форма Google — «calendarId/eventId», не URL:
     * запросы там идут на фиксированный корень API, поэтому для неё проверки
     * нет (origin null).
     */
    private fun requireAccountOrigin(account: CaldavAccountEntity, href: String) {
        val origin = originOf(href) ?: return
        val server = httpsOriginOf(account.serverUrl)
            ?: throw CaldavSyncException.Server("Адрес сервера не https: ${account.serverUrl}")
        if (origin != server) {
            throw CaldavSyncException.Server("Ссылка объекта вне адреса сервера: $href")
        }
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
        !remote.metadataFromIcs && !remote.hasDescription && !remote.notReproducible &&
            existing.hasCustomPresentation()

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
                // очередь чиста, ETag и href — от свежей вычитки. Счётчик
                // локальных мутаций не двигается: перепись сервером локальной
                // правкой не бывает, а сброс обнулил бы CAS чужого PUT'а.
                dirty = false,
                localRevision = existing.localRevision,
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
        requireAccountOrigin(account, href)
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
            return settlePush(row, first.etag ?: refetchEtag(href, account, password, transport), href)
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
        when (val fresh = transport.pullEvent(href, account.username, password)) {
            // Объект исчез между вычиткой и отправкой: правка важнее —
            // публикуем заново без предусловий.
            CaldavObjectResult.NotFound -> {
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
                return settlePush(row, recreate.etag ?: refetchEtag(href, account, password, transport), href)
            }

            // Перечитка не разобралась: жив ли объект на сервере и что в
            // нём — неизвестно. PUT затёр бы чужую правку, удаление строки
            // потеряло бы нашу — ждём следующего прогона.
            is CaldavObjectResult.Unreadable -> return PushOutcome.Deferred

            is CaldavObjectResult.Found -> {
                val remote = fresh.event
                if (remote.event.modifiedAt.orZero() > row.modifiedAt.orZero()) {
                    // Серверная правка новее — наша уступает молча-но-не-молча:
                    // строка переписывается сервером, пользователь увидит снек.
                    // Перепись безвредна лишь пока строку не трогали локально
                    // (правка, вписанная в этот сетевой ход, новее обеих версий).
                    return resolveConflictWithRemote(row, remote, account)
                }
                // Наша правка новее: PUT с живым ETag от свежей перечитки.
                // Исключение — объект вне нашей модели: PUT пересобрал бы его
                // из модели и стёр чужие данные, правка остаётся локальной.
                if (remote.notReproducible) return PushOutcome.Protected
                val retry = transport.putEvent(
                    url = href,
                    ics = ics,
                    username = account.username,
                    password = password,
                    etag = remote.etag,
                    createIfAbsent = false,
                )
                if (retry.code !in 200..299) {
                    return PushOutcome.Deferred
                }
                return settlePush(row, retry.etag ?: refetchEtag(href, account, password, transport), href)
            }
        }
    }

    /**
     * Подтверждение PUT'а строки события: очередь ([dirty]) снимается только
     * CAS'ом по [ScheduledEventEntity.localRevision] — версии, которую и
     * отправляли. Строка правилась в сетевой ход — остаётся в очереди (её
     * содержимое на сервер ещё не уезжало), но свежий ETag и href
     * запоминаем: следующий PUT пойдёт с живого предусловия, а не как первая
     * публикация.
     */
    private suspend fun settlePush(row: ScheduledEventEntity, etag: String?, href: String): PushOutcome {
        if (eventDao.markPushed(row.id, etag, href, row.localRevision) == 1) return PushOutcome.Pushed
        // Строку удалили, пока PUT был в сети: правки больше нет, а удаление
        // доезжает до сервера очередью tombstones.
        return if (eventDao.rememberServerState(row.id, etag, href) == 1) PushOutcome.Stale else PushOutcome.Skipped
    }

    /**
     * Записывающий запрос принят, а сервер ETag не отдал (CalDAV отвечает
     * 204 молча; у Google «etag» в теле мог не найтись): перечитываем
     * объект точечной вычиткой. Иначе следующая локальная правка ушла бы
     * PUT'ом вовсе без предусловия — serverHref уже есть, If-None-Match:*
     * не ставится, If-Match выставлять нечем — и вслепую затёрла бы чужую
     * версию, вписанную после нашей полной вычитки. Не перечиталось
     * (объект исчез или не прочитался) — оставляем null: без свежего ETag
     * обновление защищённым не считаем, предусловие восстановит ближайшая
     * полная вычитка ([applyRemote] записывает ETag из снапшота).
     */
    private suspend fun refetchEtag(href: String, account: CaldavAccountEntity, password: String, transport: CaldavApi): String? =
        when (val fresh = transport.pullEvent(href, account.username, password)) {
            is CaldavObjectResult.Found -> fresh.event.etag

            CaldavObjectResult.NotFound,
            is CaldavObjectResult.Unreadable,
            -> null
        }

    /**
     * Возврат в базу объекта, чьё локальное удаление уступило серверной
     * правке: та же форма вставки, что у полной вычитки, — заметка в свою
     * таблицу, событие на позицию «максимум этого дня + 1» с припиской
     * аккаунта. Tombstone снимается этим же прогоном (в той же транзакции),
     * поэтому следующая вычитка объект уже не пропустит.
     */
    private suspend fun restoreRemote(remote: CaldavRemoteEvent, account: CaldavAccountEntity) {
        if (noteBySummary(remote.event.title)) {
            // То же правило, что у вычитки: день другой подписки не затираем.
            val existing = noteDao.getByDateOnce(remote.event.date)
            if (existing == null || existing.caldavAccountId == null || existing.caldavAccountId == account.id) {
                noteDao.insert(remote.toNoteEntity(account.id))
            }
        } else {
            eventDao.insert(
                remote.event.toEntity().copy(
                    id = 0,
                    caldavAccountId = account.id,
                    remoteImported = true,
                    position = eventDao.getMaxPosition(remote.event.date) + 1,
                    dirty = false,
                    etag = remote.etag,
                    serverHref = remote.href,
                ),
            )
        }
    }

    /**
     * Примение серверной копии из конфликтного пути: перепись безвредна,
     * лишь пока строку локально не трогали с момента снапшота [snapshot]
     * (сверка счётчика внутри транзакции, по свежему чтению). Изменилась —
     * правка остаётся и в очереди, и в содержимом, следующий прогон разрешит
     * конфликт по честным меткам; исчезла вовсе (её удалили в сетевой ход) —
     * переписывать нечего, дальше работает очередь DELETE.
     */
    private suspend fun resolveConflictWithRemote(
        snapshot: ScheduledEventEntity,
        remote: CaldavRemoteEvent,
        account: CaldavAccountEntity,
    ): PushOutcome {
        var outcome = PushOutcome.Skipped
        tx.inTransaction {
            val current = eventDao.getByUid(account.id, snapshot.uid)
            outcome = when {
                current == null -> PushOutcome.Skipped

                current.localRevision != snapshot.localRevision -> PushOutcome.Stale

                else -> {
                    applyRemote(current, remote, account)
                    PushOutcome.Overwritten
                }
            }
        }
        return outcome
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
        // Фактический UID серверного носителя — идентичность очереди
        // удалений и подавления вычитки (баг 15): веб-созданный объект
        // живёт под своим uid, производный от даты его не описывает.
        remoteUid = remote.event.uid,
        // Строка переписана сервером — локальной правки нет: очередь чиста,
        // ETag и href — от свежей вычитки. Счётчик локальных мутаций не
        // двигается: перепись сервером — не локальная правка (см. pushRow).
        dirty = false,
        localRevision = existing.localRevision,
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
        remoteUid = event.uid,
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
        requireAccountOrigin(account, href)
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
            return settleNotePush(row, first.etag ?: refetchEtag(href, account, password, transport), href)
        }
        when (first.code) {
            412, 404 -> Unit
            else -> return PushOutcome.Deferred
        }

        // Конфликт: серверный объект уже не тот (412) или исчез (404).
        when (val fresh = transport.pullEvent(href, account.username, password)) {
            CaldavObjectResult.NotFound -> {
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
                return settleNotePush(row, recreate.etag ?: refetchEtag(href, account, password, transport), href)
            }

            // Нечитаемая перечитка — о серверной копии ничего не известно
            // (см. pushRow): заметка остаётся в очереди до следующего прогона.
            is CaldavObjectResult.Unreadable -> return PushOutcome.Deferred

            is CaldavObjectResult.Found -> {
                val remote = fresh.event
                if (remote.event.modifiedAt.orZero() > row.modifiedAt.orZero()) {
                    // Серверная правка новее — перепись безвредна, лишь пока
                    // текст заметки не правилась в этот сетевой ход.
                    return resolveNoteConflictWithRemote(row, remote, account)
                }
                // Носитель оброс чужими данными — PUT пересобрал бы его из
                // модели заметки и стёр их; текст остаётся в очереди.
                if (remote.notReproducible) return PushOutcome.Protected
                val retry = transport.putEvent(
                    url = href,
                    ics = ics,
                    username = account.username,
                    password = password,
                    etag = remote.etag,
                    createIfAbsent = false,
                )
                if (retry.code !in 200..299) {
                    return PushOutcome.Deferred
                }
                return settleNotePush(row, retry.etag ?: refetchEtag(href, account, password, transport), href)
            }
        }
    }

    /** Подтверждение PUT'а заметки — CAS по localRevision, зеркало [settlePush]. */
    private suspend fun settleNotePush(row: CalendarNoteEntity, etag: String?, href: String): PushOutcome {
        if (noteDao.markPushed(row.eventDate, etag, href, row.localRevision) == 1) return PushOutcome.Pushed
        // Фактический uid не перепроверяли (точечная вычитка тела не даёт) —
        // строка сохраняет известный; полная вычитка освежит.
        return if (noteDao.rememberServerState(row.eventDate, etag, href, null) == 1) PushOutcome.Stale else PushOutcome.Skipped
    }

    /**
     * Примятие серверной заметки из конфликтного пути — зеркало
     * [resolveConflictWithRemote]: только при неизменённой строке.
     */
    private suspend fun resolveNoteConflictWithRemote(
        snapshot: CalendarNoteEntity,
        remote: CaldavRemoteEvent,
        account: CaldavAccountEntity,
    ): PushOutcome {
        var outcome = PushOutcome.Skipped
        tx.inTransaction {
            val current = noteDao.getByDateOnce(snapshot.eventDate)
            outcome = when {
                current == null -> PushOutcome.Skipped

                current.localRevision != snapshot.localRevision -> PushOutcome.Stale

                else -> {
                    noteDao.insert(applyNoteRemote(current, remote, account))
                    PushOutcome.Overwritten
                }
            }
        }
        return outcome
    }
}
