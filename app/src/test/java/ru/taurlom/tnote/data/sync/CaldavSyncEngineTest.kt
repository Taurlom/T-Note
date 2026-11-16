package ru.taurlom.tnote.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity
import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Движок синхронизации против фейков DAO/сети: diff по UID, LWW для
 * dirty-строк и очередь отправки — без Room. Транзакция подменена прямым
 * исполнением блока.
 */
class CaldavSyncEngineTest {

    private val account = CaldavAccountEntity(
        id = 42,
        serverUrl = "https://caldav.yandex.ru",
        username = "taur-lom@yandex.ru",
        collectionUrl = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/",
        collectionName = "T-Note",
    )

    private val fakeEventDao = FakeEventDao()
    private val fakeNoteDao = FakeNoteDao()
    private val fakeCaldavDao = FakeCaldavDao().apply { accounts += account }
    private val fakeTombstoneDao = FakeTombstoneDao()

    private fun engine(api: FakeApi) = CaldavSyncEngine(
        eventDao = fakeEventDao,
        noteDao = fakeNoteDao,
        caldavDao = fakeCaldavDao,
        tombstoneDao = fakeTombstoneDao,
        api = api,
        tx = SyncTransaction { block -> block() },
        clock = Clock.fixed(Instant.ofEpochMilli(1234567890L), ZoneOffset.UTC),
    )

    /** Объект сервера: с адресом и ETag — diff и предусловия этапа 2 их ждут. */
    private fun remoteEvent(
        uid: String,
        date: String = "2026-02-17",
        title: String = "Из Яндекса",
        modifiedAt: Long? = null,
        etag: String = "\"$uid\"",
    ) = CaldavRemoteEvent(
        href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/$uid.ics",
        etag = etag,
        event = ScheduledEvent(
            uid = uid,
            date = date,
            title = title,
            type = ScheduledEventType.REGULAR,
            modifiedAt = modifiedAt,
        ),
    )

    /**
     * Ставит строку в очередь отправки — как репозиторий делает при правке:
     * dirty, свежая метка времени и шаг счётчика локальных мутаций (по нему
     * движок подтверждает PUT).
     */
    private fun markDirty(id: Long, modifiedAt: Long, title: String? = null) {
        fakeEventDao.rows.replaceAll { row ->
            if (row.id == id) {
                row.copy(
                    dirty = true,
                    localRevision = row.localRevision + 1,
                    modifiedAt = modifiedAt,
                    title = title ?: row.title,
                )
            } else {
                row
            }
        }
    }

    @Test
    fun `first pull inserts rows attributed to account with per-day positions`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"), remoteEvent("u2"))))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 2, updated = 0, removed = 0), result)
        fakeEventDao.rows.let { rows ->
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.caldavAccountId == 42L })
            assertEquals(listOf(0, 1), rows.map { it.position })
            // Из вычитки запомнены адрес и ETag — на них встанут PUT'ы.
            assertTrue(rows.all { it.serverHref?.endsWith(".ics") == true })
            assertTrue(rows.all { it.etag != null })
        }
        assertEquals(1234567890L, fakeCaldavDao.lastSyncAt)
        assertNull(fakeCaldavDao.lastError)
    }

    @Test
    fun `second pull updates rows keeping id and position`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1", title = "Первый"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        api.pull = CaldavPull(listOf(remoteEvent("u1", title = "Переименован")))
        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 0, updated = 1, removed = 0), result)
        fakeEventDao.rows.let { rows ->
            assertEquals(1, rows.size)
            assertEquals("Переименован", rows[0].title)
            assertEquals(1L, rows[0].id)
            assertEquals(0, rows[0].position)
            assertEquals(42L, rows[0].caldavAccountId)
        }
    }

    @Test
    fun `pull without tnote metadata keeps local presentation fields`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Отделка пользователя: вне очереди отправки, но Яндекс при PUT
        // выбрасывает X-TNOTE-* — его копия приходит без иконки/цвета.
        fakeEventDao.rows.replaceAll { row ->
            row.copy(icon = "FITNESS", colorArgb = 0xFFE53935L, hidePast = true)
        }
        api.pull = CaldavPull(
            listOf(remoteEvent("u1", title = "Переименован").copy(metadataFromIcs = false)),
        )
        val result = engine.sync(account, "pwd")

        // Отделка — своя, содержимое — серверное; строка с отделкой уходит
        // в очередь бэкфилла: маркер донесёт её другим устройствам.
        assertEquals(CaldavSyncResult(added = 0, updated = 1, removed = 0, pushed = 1), result)
        assertTrue(api.putCalls.single().ics.contains("[T-Note] icon=FITNESS"))
        fakeEventDao.rows.let { rows ->
            // Содержимое — серверное, отделка — своя: стирать её дефолтами
            // перепись не должна.
            assertEquals("Переименован", rows[0].title)
            assertEquals("FITNESS", rows[0].icon)
            assertEquals(0xFFE53935L, rows[0].colorArgb)
            assertTrue(rows[0].hidePast)
        }
    }

    @Test
    fun `pull with tnote metadata overwrites presentation fields`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")
        fakeEventDao.rows.replaceAll { row ->
            row.copy(icon = "FITNESS", colorArgb = 0xFFE53935L, hidePast = true)
        }
        // Полноценный сервер вернул X-TNOTE-*: его копия правит и отделку.
        api.pull = CaldavPull(listOf(remoteEvent("u1", title = "С сервера")))
        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 0, updated = 1, removed = 0), result)
        fakeEventDao.rows.let { rows ->
            assertEquals("NOTE", rows[0].icon)
            assertEquals(ScheduledEvent.DEFAULT_COLOR, rows[0].colorArgb)
            assertFalse(rows[0].hidePast)
        }
    }

    // ── Бэкфилл маркера: отделка старых событий, отправленных без маркера ──

    @Test
    fun `imported event with custom styling is backfilled on later pull`() = runBlocking {
        // Живой сценарий: событие создано в клиенте Яндекса, импортировано
        // (hex-uid), пользователь назначил ему иконку/цвет в T-Note, правка
        // ушла старой версией без маркера. Вычитка ставит строку в очередь,
        // PUT донесёт отделку маркером — другим устройствам не обязательно
        // ждать следующей правки.
        val uid = "4bd53b9bccd78a2facebf9af85533b9b"
        val api = FakeApi(CaldavPull(listOf(remoteEvent(uid))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Пользовательская отделка уже на сервере и в базе (правка 1.26.2).
        fakeEventDao.rows.replaceAll { row ->
            row.copy(icon = "FITNESS", colorArgb = 0xFFE53935L, hidePast = true)
        }
        // Серверная копия без метаданных (X-* Яндекс выбросил, маркера нет).
        api.pull = CaldavPull(listOf(remoteEvent(uid).copy(metadataFromIcs = false)))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, updated = 1, removed = 0, pushed = 1), result)
        val put = api.putCalls.single()
        assertTrue(put.ics.contains("[T-Note] icon=FITNESS"))
        assertTrue(put.ics.contains("color=#FFE53935"))
        assertTrue(put.ics.contains("hide=1"))
        assertFalse(fakeEventDao.rows.single().dirty)
    }

    @Test
    fun `event with default styling is never backfilled`() = runBlocking {
        // Импортированное событие с дефолтной отделкой: синхронизировать
        // нечего, а перезапись потеряла бы форму веб-Яндекса — в очередь
        // строка не встаёт.
        val api = FakeApi(
            CaldavPull(listOf(remoteEvent("93k80elgcjxjex0b0y0yandex.ru"))),
        )
        val engine = engine(api)
        engine.sync(account, "pwd")
        api.pull = CaldavPull(
            listOf(remoteEvent("93k80elgcjxjex0b0y0yandex.ru").copy(metadataFromIcs = false)),
        )
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, updated = 1, removed = 0), result)
        assertTrue(api.putCalls.isEmpty())
        assertFalse(fakeEventDao.rows.single().dirty)
    }

    @Test
    fun `object with user description is never backfilled`() = runBlocking {
        // DESCRIPTION с текстом набирал человек: PUT стёр бы его маркером,
        // поэтому бэкфилла нет даже при пользовательской отделке.
        val api = FakeApi(
            CaldavPull(
                listOf(
                    remoteEvent("4bd53b9bccd78a2facebf9af85533b9c")
                        .copy(metadataFromIcs = false, hasDescription = true),
                ),
            ),
        )
        val engine = engine(api)
        engine.sync(account, "pwd")
        fakeEventDao.rows.replaceAll { row -> row.copy(icon = "FITNESS", colorArgb = 0xFFE53935L) }
        api.pull = CaldavPull(
            listOf(
                remoteEvent("4bd53b9bccd78a2facebf9af85533b9c")
                    .copy(metadataFromIcs = false, hasDescription = true),
            ),
        )
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, updated = 1, removed = 0), result)
        assertTrue(api.putCalls.isEmpty())
        assertFalse(fakeEventDao.rows.single().dirty)
    }

    @Test
    fun `events absent on server are removed, local rows survive`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Пользовательская local-заметка того же дня: движок её не трогает.
        fakeEventDao.rows += ScheduledEventEntity(
            id = 9,
            uid = "local-uid",
            eventDate = "2026-02-17",
            title = "Моё",
            type = ScheduledEventType.REGULAR.name,
            icon = "NOTE",
            colorArgb = 0,
            repeatUnit = null,
            repeatInterval = 1,
            repeatWeekdays = null,
            repeatUntil = null,
            hidePast = false,
            position = 5,
        )
        api.pull = CaldavPull(emptyList())
        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 0, updated = 0, removed = 1), result)
        fakeEventDao.rows.let { rows ->
            assertEquals(1, rows.size)
            assertEquals("Моё", rows[0].title)
            assertNull(rows[0].caldavAccountId)
        }
    }

    @Test
    fun `incomplete pull updates rows but skips deletions`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"), remoteEvent("u2"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Одна запись вычитки не разобралась и до движка не доехала: её
        // отсутствие в списке — ещё не «удалено на сервере». u2 остаётся,
        // а причина светится на карточке аккаунта.
        api.pull = CaldavPull(listOf(remoteEvent("u1")), unreadable = 1)
        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 0, updated = 1, removed = 0), result)
        assertEquals(setOf("u1", "u2"), fakeEventDao.rows.map { it.uid }.toSet())
        assertTrue(fakeCaldavDao.lastError.orEmpty().startsWith("Вычитка неполна: 1"))
    }

    @Test
    fun `edit landed while put is in flight stays queued`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        // Версия A в очереди и уже в сети, а пользователь пишет версию B.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500, title = "Версия A")
        api.onPut = {
            fakeEventDao.rows.replaceAll { row ->
                if (row.id == 1L) {
                    row.copy(
                        dirty = true,
                        localRevision = row.localRevision + 1,
                        title = "Версия B",
                        modifiedAt = 900,
                    )
                } else {
                    row
                }
            }
        }
        api.putResults += CaldavWriteResponse(204, "\"put-e\"")

        val result = engine(api).sync(account, "pwd")

        // Ответ за версию A не имеет права снимать очередь с B: иначе
        // следующая вычитка стёрла бы правку серверной копией.
        assertEquals(0, result.pushed)
        fakeEventDao.rows.single().let { row ->
            assertTrue(row.dirty)
            assertEquals("Версия B", row.title)
            // Свежий ETag сервера всё же запомнен: следующий PUT пойдёт с
            // живого предусловия, а не как первая публикация.
            assertEquals("\"put-e\"", row.etag)
        }
        assertTrue(fakeCaldavDao.lastError.orEmpty().contains("Изменено во время отправки: 1"))

        // Следующий прогон доносит B — очередь не потерялась.
        api.onPut = null
        api.pull = CaldavPull(listOf(remoteEvent("u1", title = "Версия B", modifiedAt = 900)))
        api.putResults += CaldavWriteResponse(204, "\"put-e2\"")
        val next = engine(api).sync(account, "pwd")

        assertEquals(1, next.pushed)
        assertFalse(fakeEventDao.rows.single().dirty)
        assertTrue(api.putCalls.last().ics.contains("Версия B"))
    }

    @Test
    fun `server-newer conflict does not overwrite edit made during push`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        markDirty(fakeEventDao.rows[0].id, modifiedAt = 100, title = "Версия A")
        api.pull = CaldavPull(listOf(remoteEvent("u1")))
        api.onPut = {
            fakeEventDao.rows.replaceAll { row ->
                if (row.id == 1L) {
                    row.copy(
                        dirty = true,
                        localRevision = row.localRevision + 1,
                        title = "Версия B",
                        modifiedAt = 900,
                    )
                } else {
                    row
                }
            }
        }
        // 412: сервер изменился после вычитки; его версия новЕ снапшота A…
        api.putResults += CaldavWriteResponse(412)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1", title = "Серверная версия", modifiedAt = 800))

        val result = engine(api).sync(account, "pwd")

        // …но живая правка B (900) новее обеих, и перепись сервером стёрла бы
        // её. Строка остаётся как есть и с собственной версией уедет далее.
        assertEquals(0, result.overwritten)
        fakeEventDao.rows.single().let { row ->
            assertEquals("Версия B", row.title)
            assertTrue(row.dirty)
        }
        assertEquals(1, api.putCalls.size)
    }

    @Test
    fun `note edit landed during push stays queued`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-02-17",
            text = "Версия A",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
        )
        api.onPut = {
            fakeNoteDao.rows.replaceAll { note ->
                if (note.eventDate == "2026-02-17") {
                    note.copy(
                        dirty = true,
                        localRevision = note.localRevision + 1,
                        text = "Версия B",
                        modifiedAt = 900,
                    )
                } else {
                    note
                }
            }
        }
        api.putResults += CaldavWriteResponse(204, "\"put-note\"")

        val result = engine(api).sync(account, "pwd")

        assertEquals(0, result.pushed)
        fakeNoteDao.rows.single().let { note ->
            assertTrue(note.dirty)
            assertEquals("Версия B", note.text)
            assertEquals("\"put-note\"", note.etag)
        }
    }

    @Test
    fun `dirty edit of attributed row is uploaded with etag precondition`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Пользователь переименовал событие; репозиторий поставил dirty.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500, title = "Локальная правка")
        api.putResults += CaldavWriteResponse(204, "\"put-e\"")

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        // PUT по адресу с вычитки; If-Match — с тем ETag, что видели.
        val put = api.putCalls.single()
        assertEquals(
            "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            put.url,
        )
        assertEquals("\"u1\"", put.etag)
        assertFalse(put.createIfAbsent)
        assertTrue(put.ics.contains("Локальная правка"))
        // Очередь чиста: ждёт сервера больше нечего.
        fakeEventDao.rows.single().let { row ->
            assertFalse(row.dirty)
            assertEquals("\"put-e\"", row.etag)
        }
    }

    @Test
    fun `successful write without etag re-reads object for precondition`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Сервер принял PUT, но ETag не отдал (CalDAV-204 молчит): если
        // оставить пустой, следующая правка уйдёт без всякого предусловия.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500, title = "Локальная правка")
        api.putResults += CaldavWriteResponse(204, null)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1", etag = "\"fresh-e\""))

        val result = engine.sync(account, "pwd")

        // Правка на сервере, очередь снята — и сразу со свежим предусловием
        // из перечитки, а не вслепую до следующей полной вычитки.
        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        fakeEventDao.rows.single().let { row ->
            assertFalse(row.dirty)
            assertEquals("\"fresh-e\"", row.etag)
        }

        // Следующая правка уходит уже с перечитанным ETag (вычитка тем
        // временем подтверждает ту же версию сервера — привязка не плывёт).
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 700)
        api.pull = CaldavPull(listOf(remoteEvent("u1", etag = "\"fresh-e\"")))
        api.putResults += CaldavWriteResponse(204, "\"put-e2\"")
        engine.sync(account, "pwd")
        assertEquals("\"fresh-e\"", api.putCalls.last().etag)
    }

    @Test
    fun `write without etag and unreadable re-read leaves update unprotected`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500)
        api.putResults += CaldavWriteResponse(204, null)
        api.cannedPullEvent = CaldavObjectResult.Unreadable("PROPFIND без calendar-data")

        val result = engine.sync(account, "pwd")

        // Запись доехала — очередь снята; но без свежего ETag обновление
        // защищённым не считается: предусловие вернёт полная вычитка.
        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        val row = fakeEventDao.rows.single()
        assertFalse(row.dirty)
        assertNull(row.etag)
    }

    @Test
    fun `note write without etag re-reads carrier for precondition`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-02-17",
            text = "Версия A",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
        )
        api.putResults += CaldavWriteResponse(204, null)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u-note", etag = "\"fresh-note\""))

        val result = engine(api).sync(account, "pwd")

        assertEquals(1, result.pushed)
        fakeNoteDao.rows.single().let { note ->
            assertFalse(note.dirty)
            assertEquals("\"fresh-note\"", note.etag)
        }
    }

    @Test
    fun `remote-newer edit overwrites dirty row and is announced`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Локальная правка старше серверной: уступает.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 100)
        api.pull = CaldavPull(listOf(remoteEvent("u1", title = "Серверная правка", modifiedAt = 700)))

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, overwritten = 1), result)
        assertTrue(api.putCalls.isEmpty())
        fakeEventDao.rows.single().let { row ->
            assertEquals("Серверная правка", row.title)
            assertFalse(row.dirty)
            assertEquals("\"u1\"", row.etag)
        }
    }

    @Test
    fun `local-newer edit survives pull and is pushed after it`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Локальная правка новее серверной: строку не перетирает, но PUT.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 900, title = "Свежая правка")
        api.pull = CaldavPull(listOf(remoteEvent("u1", title = "Серверная правка", modifiedAt = 100)))

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        fakeEventDao.rows.single().let { row ->
            assertEquals("Свежая правка", row.title)
            assertFalse(row.dirty)
        }
    }

    /**
     * После восстановления бэкапа строка теряет серверную привязку
     * (href/ETag пустые), а объект на сервере может жить по веб-созданному
     * адресу. Local-wins ветка вычитки обновляет привязку отдельно от
     * содержимого: PUT уходит по серверному адресу, а не по выведенному
     * <uid>.ics — двойника ресурса с тем же UID CalDAV не видит.
     */
    @Test
    fun `local wins refreshes href and etag of restored backup row`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Форма восстановленного бэкапа: локальная правка, привязки нет.
        fakeEventDao.rows.replaceAll {
            it.copy(dirty = true, modifiedAt = 900, title = "Локальная правка", serverHref = null, etag = null)
        }
        // На сервере тот же UID живёт по нестандартному адресу своей версией,
        // но правка эта СТАРШЕ локальной — local wins.
        val webHref = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/web-created.ics"
        api.pull = CaldavPull(
            listOf(remoteEvent("u1", title = "Серверная копия", modifiedAt = 100).copy(href = webHref, etag = "\"web-e\"")),
        )
        api.putResults += CaldavWriteResponse(204, "\"pushed-e\"")

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        // Ключевое: PUT по адресу с вычитки и с её ETag, а не первая публикация.
        val put = api.putCalls.single()
        assertEquals(webHref, put.url)
        assertFalse(put.createIfAbsent)
        assertEquals("\"web-e\"", put.etag)
        assertTrue(put.ics.contains("Локальная правка"))
        fakeEventDao.rows.single().let { row ->
            assertEquals("Локальная правка", row.title)
            assertFalse(row.dirty)
            assertEquals(webHref, row.serverHref)
            assertEquals("\"pushed-e\"", row.etag)
        }
    }

    @Test
    fun `local wins refreshes carrier binding of a dirty note`() = runBlocking {
        val api = FakeApi(CaldavPull(events = emptyList(), notes = listOf(remoteNote())))
        val engine = engine(api)
        engine.sync(account, "pwd")

        fakeNoteDao.rows.replaceAll {
            it.copy(dirty = true, modifiedAt = 900, text = "Локальный текст", serverHref = null, etag = null)
        }
        val webHref = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/web-note.ics"
        api.pull = CaldavPull(
            events = emptyList(),
            notes = listOf(remoteNote(modifiedAt = 100).copy(href = webHref, etag = "\"web-note-e\"")),
        )
        api.putResults += CaldavWriteResponse(204, "\"pushed-note-e\"")

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        val put = api.putCalls.single()
        assertEquals(webHref, put.url)
        assertFalse(put.createIfAbsent)
        fakeNoteDao.rows.single().let { note ->
            assertEquals("Локальный текст", note.text)
            assertFalse(note.dirty)
            assertEquals(webHref, note.serverHref)
            assertEquals("\"pushed-note-e\"", note.etag)
        }
    }

    @Test
    fun `dirty row that vanished from server is resurrected by put`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Правка поставлена в очередь, а объект исчез с сервера: вычитка
        // отдаёт устаревший If-Match → 404; точечная перечитка — «объекта
        // нет»; публикация создаёт его заново (If-None-Match:*).
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500, title = "Локальная правка")
        api.pull = CaldavPull(emptyList())
        api.putResults += CaldavWriteResponse(404)
        api.cannedPullEvent = CaldavObjectResult.NotFound
        api.putResults += CaldavWriteResponse(201, "\"redo\"")

        val result = engine.sync(account, "pwd")

        // PUT состоялся на пустом месте — правка воскресила удалённый объект,
        // конфликт явный: счётчик resurrected.
        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1, resurrected = 1), result)
        // Первая попытка — If-Match-обновление, вторая — безусловное создание.
        assertFalse(api.putCalls[0].createIfAbsent)
        assertTrue(api.putCalls[1].createIfAbsent)
        fakeEventDao.rows.single().let { row ->
            assertEquals("Локальная правка", row.title)
            assertFalse(row.dirty)
            assertEquals("\"redo\"", row.etag)
        }
    }

    @Test
    fun `conflict after stale etag respects last-write-wins`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Сервер изменён ПОСЛЕ вычитки, но ДО отправки: устаревший ETag
        // даёт 412, перечитка показывает серверную правку новее — уступаем.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 100, title = "Старая правка")
        api.pull = CaldavPull(listOf(remoteEvent("u1")))
        api.putResults += CaldavWriteResponse(412)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1", title = "Сервер обгоняет", modifiedAt = 800))

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, overwritten = 1), result)
        assertEquals(1, api.putCalls.size)
        fakeEventDao.rows.single().let { row ->
            assertEquals("Сервер обгоняет", row.title)
            assertFalse(row.dirty)
        }
    }

    @Test
    fun `conflict after stale etag with local newer edit retries put`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Зеркальный случай: правка новее серверной — повторный PUT уже
        // с живым ETag от перечитки.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 900, title = "Свежая правка")
        api.pull = CaldavPull(listOf(remoteEvent("u1")))
        api.putResults += CaldavWriteResponse(412)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1", title = "Сервер стареет", modifiedAt = 100))
        api.putResults += CaldavWriteResponse(204, "\"ok\"")

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        assertEquals(2, api.putCalls.size)
        assertEquals("\"u1\"", api.putCalls[1].etag)
        fakeEventDao.rows.single().let { row ->
            assertEquals("Свежая правка", row.title)
            assertFalse(row.dirty)
            assertEquals("\"ok\"", row.etag)
        }
    }

    @Test
    fun `unreadable conflict object leaves row queued instead of recreating`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        // PUT упёрся в 412, а точечная перечитка не разобралась: жив ли
        // объект на сервере и что в нём — неизвестно. Пересоздание затёрло
        // бы чужую правку, поэтому строка остаётся в очереди до следующего
        // прогона, а прогон помечается незавершённым.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500, title = "Локальная правка")
        api.pull = CaldavPull(listOf(remoteEvent("u1")))
        api.putResults += CaldavWriteResponse(412)
        api.cannedPullEvent = CaldavObjectResult.Unreadable("Нечитаемое тело объекта")

        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0), result)
        // Второго PUT не было — серверную копию не перезаписывали.
        assertEquals(1, api.putCalls.size)
        assertTrue(fakeEventDao.rows.single().dirty)
        assertTrue(fakeCaldavDao.lastError.orEmpty().contains("Не отправлено: 1"))
    }

    @Test
    fun `object href outside the account server aborts the push`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        // Адрес объекта в базе ведёт на чужой хост (подменённая база — тот же
        // класс угрозы, что импорт копии): PUT туда не уходит, прогон
        // прерывается с внятной причиной вместо отправки пароля. Вычитка —
        // пустая: если бы объект в ней был, его привязка закономерно
        // освежилась бы серверным адресом ещё до push'а (local-wins ветка),
        // а защита нужна как раз для строк, которые вычитка не видела.
        fakeEventDao.rows.replaceAll {
            it.copy(dirty = true, serverHref = "https://evil.example/x.ics")
        }
        api.pull = CaldavPull(emptyList())

        val thrown = runCatching { engine(api).sync(account, "pwd") }

        assertTrue(thrown.exceptionOrNull() is CaldavSyncException.Server)
        assertTrue(api.putCalls.isEmpty())
    }

    @Test
    fun `tombstone keeps deleted event away from pull and clears after delete`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        // Пользователь удалил импортированное событие: репозиторий стёр
        // строку и записал tombstone. Сервер всё ещё помнит событие.
        fakeEventDao.rows.clear()
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u1",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            etag = "\"u1\"",
            deletedAt = 700,
        )
        api.pull = CaldavPull(listOf(remoteEvent("u1")))
        api.deleteResults += 404 // «и так нет» — очередь всё равно чиста

        val result = engine(api).sync(account, "pwd")

        // Событие не воскресло, DELETE до serverside, tombstone стёрт.
        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        assertTrue(fakeEventDao.rows.isEmpty())
        assertEquals(
            "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            api.deleteCalls.single().url,
        )
        assertTrue(fakeTombstoneDao.rows.isEmpty())
    }

    @Test
    fun `tombstone without saved href targets derived url`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "fresh-uid",
            accountId = 42,
            href = null,
            etag = null,
            deletedAt = 700,
        )

        engine(api).sync(account, "pwd")

        // DELETE идёт по стандартному адресу <uid>.ics из адреса коллекции.
        assertEquals(
            "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/fresh-uid.ics",
            api.deleteCalls.single().url,
        )
        // ETag не сохранён — предусловия нет, удаление безусловно, как раньше.
        assertNull(api.deleteCalls.single().etag)
    }

    @Test
    fun `delete conflict yields to newer server edit and restores the object`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        // Пользователь удалил событие (deletedAt 700), а второе устройство
        // отредактировало его на сервере позже (modifiedAt 800). Сохранённый
        // ETag не совпадает — условный DELETE отвечает 412.
        fakeEventDao.rows.clear()
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u1",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            etag = "\"u1\"",
            deletedAt = 700,
        )
        api.deleteResults += 412
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1", title = "Серверная правка", modifiedAt = 800))

        val result = engine(api).sync(account, "pwd")

        // Правка новее удаления — удаление уступает: объект возвращается
        // в базу явно (restored), безусловный DELETE не уходит.
        assertEquals(1, result.restored)
        assertEquals(0, result.pushed)
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        fakeEventDao.rows.single().let { row ->
            assertEquals("Серверная правка", row.title)
            assertFalse(row.dirty)
            assertEquals(42L, row.caldavAccountId)
        }
        assertEquals(1, api.deleteCalls.size)
        assertEquals("\"u1\"", api.deleteCalls.single().etag)
    }

    @Test
    fun `delete conflict confirms deletion when server edit is older`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        // 412 есть, но серверная метка (800) СТАРШЕ момента удаления (900):
        // последняя операция — удаление, оно подтверждается безусловно.
        fakeEventDao.rows.clear()
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u1",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            etag = "\"u1\"",
            deletedAt = 900,
        )
        api.deleteResults += 412
        api.deleteResults += 204
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1", modifiedAt = 800))

        val result = engine(api).sync(account, "pwd")

        assertEquals(1, result.pushed)
        assertEquals(0, result.restored)
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        assertTrue(fakeEventDao.rows.isEmpty())
        assertEquals(listOf("\"u1\"", null), api.deleteCalls.map { it.etag })
    }

    @Test
    fun `delete conflict with vanished object closes the queue`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        fakeEventDao.rows.clear()
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u1",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            etag = "\"u1\"",
            deletedAt = 700,
        )
        api.deleteResults += 412
        // Пока движок решал конфликт, объект удалили совсем: «и так нет».
        api.cannedPullEvent = CaldavObjectResult.NotFound

        val result = engine(api).sync(account, "pwd")

        assertEquals(1, result.pushed)
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        assertTrue(fakeEventDao.rows.isEmpty())
        assertEquals(1, api.deleteCalls.size)
    }

    @Test
    fun `unreadable delete conflict defers the tombstone`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        fakeEventDao.rows.clear()
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u1",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            etag = "\"u1\"",
            deletedAt = 700,
        )
        api.deleteResults += 412
        api.cannedPullEvent = CaldavObjectResult.Unreadable("PROPFIND без calendar-data")

        val result = engine(api).sync(account, "pwd")

        // О серверной копии ничего не известно: ни удалять, ни воскрешать
        // нельзя — очередь доживёт до следующего прогона.
        assertEquals(0, result.pushed)
        assertEquals(0, result.restored)
        assertEquals(1, fakeTombstoneDao.rows.size)
        assertTrue(fakeCaldavDao.lastError.orEmpty().contains("Не отправлено: 1"))
    }

    @Test
    fun `delete conflict restores note into its own table`() = runBlocking {
        val api = FakeApi(CaldavPull(events = emptyList(), notes = listOf(remoteNote())))
        engine(api).sync(account, "pwd")

        fakeNoteDao.rows.clear()
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = noteUidFor("2026-02-17"),
            accountId = 42L,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/${noteUidFor("2026-02-17")}.ics",
            etag = "\"n1\"",
            deletedAt = 700,
        )
        api.deleteResults += 412
        api.cannedPullEvent = CaldavObjectResult.Found(remoteNote(text = "Серверный текст", modifiedAt = 800))

        val result = engine(api).sync(account, "pwd")

        assertEquals(1, result.restored)
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        fakeNoteDao.rows.single().let { note ->
            assertEquals("Серверный текст", note.text)
            assertFalse(note.dirty)
            assertEquals(42L, note.caldavAccountId)
        }
    }

    @Test
    fun `put failure defers row and lets tombstones flush`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Живой сценарий Яндекса: PUT первой публикации отдаёт 504 (невалидное
        // для сервера тело или его временный сбой). Прогон не рвётся:
        // строка остаётся в очереди, tombstone другой строки всё равно
        // доезжает, в настройках остаётся след незавершённого прогона.
        markDirty(fakeEventDao.rows[0].id, modifiedAt = 500, title = "Локальная правка")
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u2",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u2.ics",
            etag = null,
            deletedAt = 700,
        )
        api.putResults += CaldavWriteResponse(504)

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        assertTrue(fakeEventDao.rows.single().dirty)
        assertEquals(1, api.deleteCalls.size)
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        assertEquals(1234567890L, fakeCaldavDao.lastSyncAt)
        assertEquals(
            "Не отправлено: 1 (сбой сервера, повторится)",
            fakeCaldavDao.lastError,
        )
    }

    @Test
    fun `tombstone delete failure keeps row queued without aborting`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = "u1",
            accountId = 42,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/u1.ics",
            etag = null,
            deletedAt = 700,
        )
        api.deleteResults += 504

        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0), result)
        assertEquals(1, fakeTombstoneDao.rows.size)
        assertEquals(1234567890L, fakeCaldavDao.lastSyncAt)
        assertEquals(
            "Не отправлено: 1 (сбой сервера, повторится)",
            fakeCaldavDao.lastError,
        )
    }

    @Test
    fun `dirty row with broken date stays queued instead of crashing`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        engine(api).sync(account, "pwd")

        fakeEventDao.rows.replaceAll { it.copy(dirty = true, eventDate = "2026-13-40") }

        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0), result)
        assertTrue(api.putCalls.isEmpty())
        assertTrue(fakeEventDao.rows.single().dirty)
    }

    // ── Временная заморозка перезаписи (данные вне модели, overrides/вхождения) ──

    /**
     * Событие в подписке наполняли не только из приложения: PUT пересобирает
     * ресурс из модели одним VEVENT и стёр бы место, участников, напоминания,
     * DESCRIPTION человека или исключения серии. Прапка остаётся локальной,
     * строка — в очереди, причина светится на карточке аккаунта.
     */
    @Test
    fun `edit of an object with server-only fields stays local`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1").copy(notReproducible = true))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        markDirty(fakeEventDao.rows.single().id, modifiedAt = 900, title = "Локальная правка")
        api.pull = CaldavPull(listOf(remoteEvent("u1").copy(notReproducible = true)))
        val result = engine.sync(account, "pwd")

        assertTrue(api.putCalls.isEmpty())
        assertEquals(CaldavSyncResult(0, 0, 0), result)
        fakeEventDao.rows.single().let { row ->
            assertTrue("правка не должна сниматься с очереди", row.dirty)
            assertEquals("Локальная правка", row.title)
        }
        assertTrue(fakeCaldavDao.lastError?.contains("Без отправки") == true)
    }

    /**
     * Тот же объект, но правка новее серверной и ETag живой (412): точечная
     * перечитка показала чужие данные — PUT даже после конфликта не уходит.
     */
    @Test
    fun `conflict re-read of an object with server-only fields blocks the put`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")
        markDirty(fakeEventDao.rows.single().id, modifiedAt = 900, title = "Локальная правка")

        // В вычитке этого объекта не было (признак ещё не знаком), сервер
        // отвечает 412, а перечитка приносит объект с чужими полями.
        api.pull = CaldavPull(emptyList())
        api.putResults += CaldavWriteResponse(412, null)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteEvent("u1").copy(notReproducible = true))
        val result = engine.sync(account, "pwd")

        // Один PUT был отправлен (и получил 412) — повторного нет.
        assertEquals(1, api.putCalls.size)
        assertEquals(CaldavSyncResult(0, 0, 0), result)
        assertTrue(fakeEventDao.rows.single().dirty)
    }

    /**
     * Вычитка прочла не всё: про чужие строки ничего не известно, PUT идёт
     * вслепую. До полной вычитки правки импортированных событий не уезжают.
     */
    @Test
    fun `incomplete snapshot keeps imported edits local`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        val engine = engine(api)
        engine.sync(account, "pwd")

        markDirty(fakeEventDao.rows.single().id, modifiedAt = 900, title = "Локальная правка")
        api.pull = CaldavPull(listOf(remoteEvent("u1")), unreadable = 1)
        engine.sync(account, "pwd")

        assertTrue(api.putCalls.isEmpty())
        assertTrue(fakeEventDao.rows.single().dirty)
    }

    /** Событие, созданное в приложении, отправляется и при неполной вычитке. */
    @Test
    fun `own row is pushed even when the snapshot is incomplete`() = runBlocking {
        fakeEventDao.rows += ScheduledEventEntity(
            id = 9,
            uid = "own-uid",
            eventDate = "2026-02-17",
            title = "Моё",
            type = ScheduledEventType.REGULAR.name,
            colorArgb = 0,
            repeatUnit = null,
            repeatInterval = 1,
            repeatWeekdays = null,
            repeatUntil = null,
            icon = "NOTE",
            hidePast = false,
            caldavAccountId = 42L,
            dirty = true,
            localRevision = 1,
        )
        val api = FakeApi(CaldavPull(emptyList(), unreadable = 1))

        val result = engine(api).sync(account, "pwd")

        assertEquals(1, api.putCalls.size)
        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        assertFalse(fakeEventDao.rows.single().dirty)
    }

    /**
     * Вхождение серии Google — отдельный объект со своим id. Старая строка,
     * рождённая прежней (неверной) вычиткой, не должна возвращаться на сервер
     * самостоятельным событием: UID таких объектов транспорт присылает в
     * protectedUids.
     */
    @Test
    fun `legacy row of a series instance is not put`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("instance-uid"))))
        engine(api).sync(account, "pwd")
        markDirty(fakeEventDao.rows.single().id, modifiedAt = 900, title = "Перенесённое вхождение")

        // Новая вычитка: мастера серий есть, вхождения не импортируются.
        api.pull = CaldavPull(listOf(remoteEvent("master-uid")), protectedUids = setOf("instance-uid"))
        engine(api).sync(account, "pwd")

        assertTrue(api.putCalls.isEmpty())
        // Строка вживания серии осталась локальной (dirty) и не вычистилась
        // diff'ом: очередь правки ждёт, пока транспорт перестанет её беречь.
        val orphan = fakeEventDao.rows.firstOrNull { it.uid == "instance-uid" } ?: error("строка вхождения пропала")
        assertTrue(orphan.dirty)
        assertEquals(2, fakeEventDao.rows.size)
    }

    /**
     * Бэкфилл маркера — PUT по объекту, который правил не человек? Нет: если
     * на сервере есть данные вне модели, строка даже не встаёт в очередь.
     */
    @Test
    fun `marker backfill skips objects with server-only fields`() = runBlocking {
        val uid = "4bd53b9bccd78a2facebf9af85533b9d"
        val api = FakeApi(CaldavPull(listOf(remoteEvent(uid))))
        engine(api).sync(account, "pwd")

        fakeEventDao.rows.replaceAll { it.copy(icon = "FITNESS", colorArgb = 0xFFE53935L) }
        api.pull = CaldavPull(listOf(remoteEvent(uid).copy(metadataFromIcs = false, notReproducible = true)))
        val result = engine(api).sync(account, "pwd")

        assertTrue(api.putCalls.isEmpty())
        assertFalse(fakeEventDao.rows.single().dirty)
        assertEquals(CaldavSyncResult(0, updated = 1, removed = 0), result)
    }

    @Test
    fun `sync success marks account synced and error-free`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 0, updated = 0, removed = 0), result)
        assertEquals(1234567890L, fakeCaldavDao.lastSyncAt)
    }

    /**
     * Подписку отключили, пока вычитка была в сети: применять разницу
     * некому. Строки с мёртвым accountId не почистит уже никто (diff и обе
     * очереди привязаны к аккаунту), а PUT/DELETE от его имени — ходы на
     * выключенный сервер. Прогон сворачивается до вставок, отправки,
     * удалений и отметки о синхронизации.
     */
    @Test
    fun `pull is dropped when the account was disconnected mid-flight`() = runBlocking {
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-03-01",
            text = "Локальная правка",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
        )
        fakeTombstoneDao.rows += CaldavTombstoneEntity(uid = "u9", accountId = 42L, deletedAt = 1)
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"))))
        // disconnect закоммитился между сетевым ходом вычитки и её применением.
        fakeCaldavDao.accounts.clear()

        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0), result)
        assertTrue(api.putCalls.isEmpty())
        assertTrue(api.deleteCalls.isEmpty())
        // Ни одна строка вычитки не вписана, локальные не тронуты.
        assertTrue(fakeEventDao.rows.isEmpty())
        fakeNoteDao.rows.single().let { note ->
            assertTrue(note.dirty)
            assertNull(note.serverHref)
        }
        // Очередь удалений и отметка о прогоне остались как были.
        assertEquals(1, fakeTombstoneDao.rows.size)
        assertEquals(0L, fakeCaldavDao.lastSyncAt)
    }

    // ── Заметки: обмен событиями-носителями («✍️ Заметка», текст в DESCRIPTION) ──

    /** Серверный объект заметки: текст едет рядом с событием (noteText). */
    private fun remoteNote(date: String = "2026-02-17", text: String = "Молоко", modifiedAt: Long? = null) = CaldavRemoteEvent(
        href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/${noteUidFor(date)}.ics",
        etag = "\"note-$date\"",
        event = ScheduledEvent(
            uid = noteUidFor(date),
            date = date,
            title = NOTE_SUMMARY,
            type = ScheduledEventType.REGULAR,
            modifiedAt = modifiedAt,
        ),
        noteText = text,
    )

    @Test
    fun `note pull inserts note attributed to account`() = runBlocking {
        val api = FakeApi(CaldavPull(events = emptyList(), notes = listOf(remoteNote(text = "Молоко"))))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 1, updated = 0, removed = 0), result)
        fakeNoteDao.rows.single().let { note ->
            assertEquals("2026-02-17", note.eventDate)
            assertEquals("Молоко", note.text)
            assertEquals(42L, note.caldavAccountId)
            assertFalse(note.dirty)
            assertTrue(note.serverHref!!.endsWith(".ics"))
            assertEquals("\"note-2026-02-17\"", note.etag)
        }
        // Заметка не протекает в события: таблицы не смешиваются.
        assertTrue(fakeEventDao.rows.isEmpty())
    }

    @Test
    fun `dirty note newer on server is overwritten`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        val engine = engine(api)

        // Локальная правка в очереди (старее серверной).
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-02-17",
            text = "Локальная заметка",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
        )
        api.putResults += CaldavWriteResponse(412, null)
        api.cannedPullEvent = CaldavObjectResult.Found(remoteNote(text = "Серверная заметка", modifiedAt = 999))

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, 0, overwritten = 1), result)
        fakeNoteDao.rows.single().let { note ->
            assertEquals("Серверная заметка", note.text)
            assertFalse(note.dirty)
            assertEquals(999L, note.modifiedAt)
        }
    }

    @Test
    fun `dirty note is uploaded as carrier event with description`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-02-17",
            text = "Молоко и хлеб",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
        )
        api.putResults += CaldavWriteResponse(204, "\"put-note\"")

        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        val put = api.putCalls.single()
        // Ключ на сервере — детерминированный UID от даты (в href @ кодируется).
        assertEquals(
            "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/note-2026-02-17%40taurlom.ics",
            put.url,
        )
        assertTrue(put.createIfAbsent)
        assertTrue(put.ics.contains("✍\uFE0F Заметка"))
        assertTrue(put.ics.contains("Молоко и хлеб"))
        // Очередь чиста, href/ETag запомнены для следующего If-Match.
        fakeNoteDao.rows.single().let { note ->
            assertFalse(note.dirty)
            assertEquals("\"put-note\"", note.etag)
            assertTrue(note.serverHref!!.endsWith("note-2026-02-17%40taurlom.ics"))
        }
    }

    @Test
    fun `note absent on server is removed, local note survives`() = runBlocking {
        val api = FakeApi(CaldavPull(events = emptyList(), notes = listOf(remoteNote())))
        val engine = engine(api)
        engine.sync(account, "pwd")

        // Локальная заметка без приписки движку не принадлежит.
        fakeNoteDao.rows += CalendarNoteEntity(eventDate = "2026-03-01", text = "Моя")
        api.pull = CaldavPull(emptyList())
        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, removed = 1), result)
        fakeNoteDao.rows.single().let { note ->
            assertEquals("2026-03-01", note.eventDate)
            assertNull(note.caldavAccountId)
        }
    }

    @Test
    fun `pulled note with tombstone uid is not resurrected`() = runBlocking {
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = noteUidFor("2026-02-17"),
            accountId = 42L,
            deletedAt = 1,
        )
        val api = FakeApi(CaldavPull(events = emptyList(), notes = listOf(remoteNote())))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        // DELETE по выведенному из UID адресу состоялся — очередь пуста.
        assertEquals(1, api.deleteCalls.size)
        assertTrue(fakeNoteDao.rows.isEmpty())
    }

    /**
     * Заметку дня удалили (её UID — детерминирован от даты), до серверного
     * DELETE написали новую на тот же день: PUT и DELETE одной идентичности
     * в одном прогоне стёрли бы свежий объект на сервере, а следующая
     * вычитка вычистила бы и локальную строку — заметка пропала бы совсем.
     * Пересоздание снимает очередь удаления (CalendarRepositoryImpl.saveNote),
     * движок дополнительно аннулирует устаревший tombstone без сетевого хода.
     */
    @Test
    fun `tombstone of a recreated note does not delete the fresh object`() = runBlocking {
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = noteUidFor("2026-02-17"),
            accountId = 42L,
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/note-2026-02-17%40taurlom.ics",
            deletedAt = 1,
        )
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-02-17",
            text = "Молоко и хлеб",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
            localRevision = 1,
        )
        val api = FakeApi(CaldavPull(events = emptyList(), notes = emptyList()))
        val result = engine(api).sync(account, "pwd")

        // Свежая заметка опубликована, а старого DELETE не было вовсе.
        assertEquals(1, api.putCalls.size)
        assertTrue(api.deleteCalls.isEmpty())
        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        // Очередь удаления снята — на следующем прогоне она не сработает.
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        fakeNoteDao.rows.single().let { note ->
            assertEquals("Молоко и хлеб", note.text)
            assertFalse(note.dirty)
        }

        // Следующая вычитка: сервер держит опубликованный объект — строка цела.
        api.pull = CaldavPull(events = emptyList(), notes = listOf(remoteNote(text = "Молоко и хлеб")))
        engine(api).sync(account, "pwd")
        fakeNoteDao.rows.single().let { note ->
            assertEquals("Молоко и хлеб", note.text)
            assertFalse(note.dirty)
        }
    }

    /**
     * Правка не доехала (сбой сервера) — удалять нечего, очередь DELETE
     * легитимна: аннулирование касается только идентичностей, которые этот
     * прогон действительно опубликовал.
     */
    @Test
    fun `tombstone still deletes when the put of the same uid failed`() = runBlocking {
        fakeTombstoneDao.rows += CaldavTombstoneEntity(
            uid = noteUidFor("2026-02-17"),
            accountId = 42L,
            deletedAt = 1,
        )
        fakeNoteDao.rows += CalendarNoteEntity(
            eventDate = "2026-02-17",
            text = "Молоко и хлеб",
            createdAt = 100,
            modifiedAt = 500,
            caldavAccountId = 42L,
            dirty = true,
            localRevision = 1,
        )
        val api = FakeApi(CaldavPull(events = emptyList(), notes = emptyList()))
        api.putResults += CaldavWriteResponse(500, null)
        val result = engine(api).sync(account, "pwd")

        assertEquals(1, api.deleteCalls.size)
        assertTrue(fakeTombstoneDao.rows.isEmpty())
        // PUT не состоялся: строка осталась в очереди на отправку.
        fakeNoteDao.rows.single().let { note ->
            assertTrue(note.dirty)
            assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
        }
    }
}

private class FakeEventDao : ScheduledEventDao {
    val rows = mutableListOf<ScheduledEventEntity>()
    private var nextId = 1L

    override fun getByMonth(monthStart: String, nextMonthStart: String): Flow<List<ScheduledEventEntity>> = flowOf(emptyList())

    override fun getByType(type: String): Flow<List<ScheduledEventEntity>> = flowOf(emptyList())

    override suspend fun getByIdOnce(id: Long): ScheduledEventEntity? = rows.firstOrNull { it.id == id }

    override suspend fun getMaxPosition(date: String): Int = rows.filter { it.eventDate == date }.maxOfOrNull { it.position } ?: -1

    override suspend fun deleteByDate(date: String) {
        rows.removeAll { it.eventDate == date }
    }

    override suspend fun getByDateBefore(cutoff: String): List<ScheduledEventEntity> = rows.filter { it.eventDate < cutoff }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun insert(event: ScheduledEventEntity): Long {
        val id = if (event.id != 0L) event.id else nextId++
        rows.removeAll { it.id == id }
        rows.add(event.copy(id = id))
        return id
    }

    override suspend fun update(event: ScheduledEventEntity) {
        rows.removeAll { it.id == event.id }
        rows.add(event)
    }

    override suspend fun delete(event: ScheduledEventEntity) {
        rows.removeAll { it.id == event.id }
    }

    override suspend fun delete(events: List<ScheduledEventEntity>) {
        val ids = events.map { it.id }.toSet()
        rows.removeAll { it.id in ids }
    }

    override suspend fun getByUid(uid: String): ScheduledEventEntity? = rows.firstOrNull { it.uid == uid }

    override suspend fun deleteNotInUids(accountId: Long, uids: List<String>): Int {
        val before = rows.size
        val keep = uids.toSet()
        rows.removeAll {
            it.caldavAccountId == accountId && !it.dirty && it.uid !in keep
        }
        return before - rows.size
    }

    override suspend fun deleteByCaldavAccount(accountId: Long): Int {
        val before = rows.size
        rows.removeAll { it.caldavAccountId == accountId }
        return before - rows.size
    }

    override suspend fun detachFromAccount(accountId: Long): Int {
        var detached = 0
        rows.replaceAll { row ->
            if (row.caldavAccountId == accountId) {
                detached++
                row.copy(caldavAccountId = null, serverHref = null, etag = null, dirty = false)
            } else {
                row
            }
        }
        return detached
    }

    override suspend fun getByDateOnce(date: String): List<ScheduledEventEntity> = rows.filter { it.eventDate == date }

    override suspend fun getAttributed(): List<ScheduledEventEntity> = rows.filter { it.caldavAccountId != null }

    override suspend fun getDirtyFor(accountId: Long): List<ScheduledEventEntity> =
        rows.filter { it.dirty && it.caldavAccountId == accountId }

    /** CAS как в Room: очередь снимается только строке с отправленной версией. */
    override suspend fun markPushed(id: Long, etag: String?, href: String, sentRevision: Long): Int {
        val row = rows.firstOrNull { it.id == id && it.localRevision == sentRevision } ?: return 0
        rows.replaceAll { if (it.id == id) it.copy(dirty = false, etag = etag, serverHref = href) else it }
        return 1
    }

    override suspend fun rememberServerState(id: Long, etag: String?, href: String): Int {
        if (rows.none { it.id == id }) return 0
        rows.replaceAll { if (it.id == id) it.copy(etag = etag, serverHref = href) else it }
        return 1
    }

    override suspend fun markDirty(id: Long) {
        rows.replaceAll { row ->
            if (row.id == id) {
                row.copy(dirty = true)
            } else {
                row
            }
        }
    }

    override fun observeLocalOnlyCount(): Flow<Int> = flowOf(rows.count { it.caldavAccountId == null })

    override suspend fun attributeAll(accountId: Long): Int {
        var count = 0
        rows.replaceAll { row ->
            if (row.caldavAccountId == null) {
                count++
                row.copy(caldavAccountId = accountId, dirty = true)
            } else {
                row
            }
        }
        return count
    }
}

private class FakeNoteDao : CalendarNoteDao {
    val rows = mutableListOf<CalendarNoteEntity>()

    override fun getByMonthPrefix(monthPrefix: String): Flow<List<CalendarNoteEntity>> = flowOf(emptyList())

    override suspend fun deleteByDate(date: String) {
        rows.removeAll { it.eventDate == date }
    }

    override suspend fun getByDateOnce(date: String): CalendarNoteEntity? = rows.firstOrNull { it.eventDate == date }

    override suspend fun deleteBefore(cutoff: String): Int {
        val before = rows.size
        rows.removeAll { it.eventDate < cutoff }
        return before - rows.size
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun insert(note: CalendarNoteEntity): Long {
        rows.removeAll { it.eventDate == note.eventDate }
        rows.add(note)
        return 1
    }

    override suspend fun getAttributed(): List<CalendarNoteEntity> = rows.filter { it.caldavAccountId != null }

    override suspend fun getAttributedBefore(cutoff: String): List<CalendarNoteEntity> =
        rows.filter { it.caldavAccountId != null && it.eventDate < cutoff }

    override suspend fun getDirtyFor(accountId: Long): List<CalendarNoteEntity> =
        rows.filter { it.dirty && it.caldavAccountId == accountId }

    /** CAS как в Room: очередь снимается только строке с отправленной версией. */
    override suspend fun markPushed(date: String, etag: String?, href: String, sentRevision: Long): Int {
        val row = rows.firstOrNull { it.eventDate == date && it.localRevision == sentRevision } ?: return 0
        rows.replaceAll { if (it.eventDate == date) it.copy(dirty = false, etag = etag, serverHref = href) else it }
        return 1
    }

    override suspend fun rememberServerState(date: String, etag: String?, href: String): Int {
        if (rows.none { it.eventDate == date }) return 0
        rows.replaceAll { if (it.eventDate == date) it.copy(etag = etag, serverHref = href) else it }
        return 1
    }

    override suspend fun deleteNotInDates(accountId: Long, dates: List<String>): Int {
        val before = rows.size
        val keep = dates.toSet()
        rows.removeAll {
            it.caldavAccountId == accountId && !it.dirty && it.eventDate !in keep
        }
        return before - rows.size
    }

    override suspend fun attributeAll(accountId: Long): Int {
        var count = 0
        rows.replaceAll { row ->
            if (row.caldavAccountId == null) {
                count++
                row.copy(caldavAccountId = accountId, dirty = true)
            } else {
                row
            }
        }
        return count
    }

    override suspend fun detachFromAccount(accountId: Long): Int {
        var detached = 0
        rows.replaceAll { row ->
            if (row.caldavAccountId == accountId) {
                detached++
                row.copy(caldavAccountId = null, serverHref = null, etag = null, dirty = false)
            } else {
                row
            }
        }
        return detached
    }
}

private class FakeCaldavDao : CaldavDao {
    /** Строки аккаунтов: движок перед применением вычитки сверяет, что подписка жива. */
    val accounts = mutableListOf<CaldavAccountEntity>()
    var lastSyncAt = 0L
    var lastError: String? = null

    override fun observeAll(): Flow<List<CaldavAccountEntity>> = flowOf(accounts.toList())

    override suspend fun getAll(): List<CaldavAccountEntity> = accounts.toList()

    override suspend fun getById(id: Long): CaldavAccountEntity? = accounts.firstOrNull { it.id == id }

    override suspend fun insert(account: CaldavAccountEntity): Long {
        accounts += account
        return account.id
    }

    override suspend fun deleteById(id: Long) {
        accounts.removeAll { it.id == id }
    }

    override suspend fun markSynced(id: Long, time: Long) {
        lastSyncAt = time
        lastError = null
    }

    override suspend fun markFailed(id: Long, error: String?) {
        lastError = error
    }
}

private class FakeTombstoneDao : CaldavTombstoneDao {
    val rows = mutableListOf<CaldavTombstoneEntity>()

    override suspend fun getAllFor(accountId: Long): List<CaldavTombstoneEntity> = rows.filter { it.accountId == accountId }

    override suspend fun getByUid(uid: String): CaldavTombstoneEntity? = rows.firstOrNull { it.uid == uid }

    override suspend fun insert(tombstone: CaldavTombstoneEntity) {
        rows.removeAll { it.uid == tombstone.uid }
        rows += tombstone
    }

    override suspend fun deleteByUid(uid: String): Int {
        val before = rows.size
        rows.removeAll { it.uid == uid }
        return before - rows.size
    }

    override suspend fun deleteForIdentity(uid: String, accountId: Long): Int {
        val before = rows.size
        rows.removeAll { it.uid == uid && it.accountId == accountId }
        return before - rows.size
    }

    override suspend fun deleteAllFor(accountId: Long): Int {
        val before = rows.size
        rows.removeAll { it.accountId == accountId }
        return before - rows.size
    }
}

private class FakeApi(pull: CaldavPull) : CaldavApi {

    data class PutCall(val url: String, val etag: String?, val createIfAbsent: Boolean, val ics: String)

    data class DeleteCall(val url: String, val etag: String?)

    var pull: CaldavPull = pull

    /** Очередь ответов PUT; когда кончилась — «успех 204 со свежим ETag». */
    val putResults = mutableListOf<CaldavWriteResponse>()

    /** Скриптованный ответ точечной вычитки (конфликтный путь 412/404). */
    var cannedPullEvent: CaldavObjectResult = CaldavObjectResult.NotFound

    /**
     * Крюк «во время сети»: вызывается внутри putEvent — имитирует правку
     * пользователя, вписанную, пока версия строки уже в пути.
     */
    var onPut: (() -> Unit)? = null

    val putCalls = mutableListOf<PutCall>()
    val deleteCalls = mutableListOf<DeleteCall>()
    val deleteResults = mutableListOf<Int>()

    override suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef> = emptyList()

    override suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull = pull

    override suspend fun putEvent(
        url: String,
        ics: String,
        username: String,
        password: String,
        etag: String?,
        createIfAbsent: Boolean,
    ): CaldavWriteResponse {
        putCalls += PutCall(url, etag, createIfAbsent, ics)
        onPut?.invoke()
        return putResults.removeFirstOrNull() ?: CaldavWriteResponse(204, "\"pushed-$etag\"")
    }

    override suspend fun deleteEvent(url: String, username: String, password: String, etag: String?): Int {
        deleteCalls += DeleteCall(url, etag)
        return deleteResults.removeFirstOrNull() ?: 204
    }

    override suspend fun pullEvent(url: String, username: String, password: String): CaldavObjectResult = cannedPullEvent

    override fun objectHref(collectionUrl: String, uid: String): String = deriveObjectHref(collectionUrl, uid)
}
