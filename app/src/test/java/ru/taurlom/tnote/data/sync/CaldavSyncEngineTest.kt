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
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
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
    private val fakeCaldavDao = FakeCaldavDao()
    private val fakeTombstoneDao = FakeTombstoneDao()

    private fun engine(api: FakeApi) = CaldavSyncEngine(
        eventDao = fakeEventDao,
        caldavDao = fakeCaldavDao,
        tombstoneDao = fakeTombstoneDao,
        api = api,
        tx = SyncTransaction { block -> block() },
        clock = Clock.fixed(Instant.ofEpochMilli(1234567890L), ZoneOffset.UTC),
    )

    /** Объект сервера: с адресом и ETag — diff и предусловия этапа 2 их ждут. */
    private fun remoteEvent(uid: String, date: String = "2026-02-17", title: String = "Из Яндекса", modifiedAt: Long? = null) =
        CaldavRemoteEvent(
            href = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-1/$uid.ics",
            etag = "\"$uid\"",
            event = ScheduledEvent(
                uid = uid,
                date = date,
                title = title,
                type = ScheduledEventType.REGULAR,
                modifiedAt = modifiedAt,
            ),
        )

    /** Ставит строку в очередь отправки — как репозиторий делает при правке. */
    private fun markDirty(id: Long, modifiedAt: Long, title: String? = null) {
        fakeEventDao.rows.replaceAll { row ->
            if (row.id == id) {
                row.copy(dirty = true, modifiedAt = modifiedAt, title = title ?: row.title)
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
        api.cannedPullEvent = null
        api.putResults += CaldavWriteResponse(201, "\"redo\"")

        val result = engine.sync(account, "pwd")

        assertEquals(CaldavSyncResult(0, 0, 0, pushed = 1), result)
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
        api.cannedPullEvent = remoteEvent("u1", title = "Сервер обгоняет", modifiedAt = 800)

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
        api.cannedPullEvent = remoteEvent("u1", title = "Сервер стареет", modifiedAt = 100)
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
            api.deleteCalls.single(),
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
            api.deleteCalls.single(),
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

    @Test
    fun `sync success marks account synced and error-free`() = runBlocking {
        val api = FakeApi(CaldavPull(emptyList()))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 0, updated = 0, removed = 0), result)
        assertEquals(1234567890L, fakeCaldavDao.lastSyncAt)
    }
}

private class FakeEventDao : ScheduledEventDao {
    val rows = mutableListOf<ScheduledEventEntity>()
    private var nextId = 1L

    override fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEventEntity>> = flowOf(emptyList())

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

    override suspend fun getByDateOnce(date: String): List<ScheduledEventEntity> = rows.filter { it.eventDate == date }

    override suspend fun getAttributed(): List<ScheduledEventEntity> = rows.filter { it.caldavAccountId != null }

    override suspend fun getDirtyFor(accountId: Long): List<ScheduledEventEntity> =
        rows.filter { it.dirty && it.caldavAccountId == accountId }

    override suspend fun markPushed(id: Long, etag: String?, href: String) {
        rows.replaceAll { row ->
            if (row.id == id) {
                row.copy(dirty = false, etag = etag, serverHref = href)
            } else {
                row
            }
        }
    }
}

private class FakeCaldavDao : CaldavDao {
    var lastSyncAt = 0L
    var lastError: String? = null

    override fun observeAll(): Flow<List<CaldavAccountEntity>> = flowOf(emptyList())

    override suspend fun getAll(): List<CaldavAccountEntity> = emptyList()

    override suspend fun getById(id: Long): CaldavAccountEntity? = null

    override suspend fun insert(account: CaldavAccountEntity): Long = 1

    override suspend fun deleteById(id: Long) = Unit

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

    override suspend fun deleteAllFor(accountId: Long): Int {
        val before = rows.size
        rows.removeAll { it.accountId == accountId }
        return before - rows.size
    }
}

private class FakeApi(pull: CaldavPull) : CaldavApi {

    data class PutCall(val url: String, val etag: String?, val createIfAbsent: Boolean, val ics: String)

    var pull: CaldavPull = pull

    /** Очередь ответов PUT; когда кончилась — «успех 204 со свежим ETag». */
    val putResults = mutableListOf<CaldavWriteResponse>()

    /** Скриптованный ответ точечной вычитки (конфликтный путь 412/404). */
    var cannedPullEvent: CaldavRemoteEvent? = null

    val putCalls = mutableListOf<PutCall>()
    val deleteCalls = mutableListOf<String>()
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
        return putResults.removeFirstOrNull() ?: CaldavWriteResponse(204, "\"pushed-$etag\"")
    }

    override suspend fun deleteEvent(url: String, username: String, password: String): Int {
        deleteCalls += url
        return deleteResults.removeFirstOrNull() ?: 204
    }

    override suspend fun pullEvent(url: String, username: String, password: String): CaldavRemoteEvent? = cannedPullEvent

    override fun objectHref(collectionUrl: String, uid: String): String = deriveObjectHref(collectionUrl, uid)
}
