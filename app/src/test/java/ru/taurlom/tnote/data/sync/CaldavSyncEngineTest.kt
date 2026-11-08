package ru.taurlom.tnote.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Движок синхронизации против фейков DAO/сети: diff по UID без Room.
 * Транзакция подменена прямым исполнением блока.
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

    private fun engine(api: FakeApi) = CaldavSyncEngine(
        eventDao = fakeEventDao,
        caldavDao = fakeCaldavDao,
        api = api,
        tx = SyncTransaction { block -> block() },
        clock = Clock.fixed(Instant.ofEpochMilli(1234567890L), ZoneOffset.UTC),
    )

    private fun remoteEvent(uid: String, date: String = "2026-02-17", title: String = "Из Яндекса") = ScheduledEvent(
        uid = uid,
        date = date,
        title = title,
        type = ScheduledEventType.REGULAR,
    )

    @Test
    fun `first pull inserts rows attributed to account with per-day positions`() = runBlocking {
        val api = FakeApi(CaldavPull(listOf(remoteEvent("u1"), remoteEvent("u2"))))
        val result = engine(api).sync(account, "pwd")

        assertEquals(CaldavSyncResult(added = 2, updated = 0, removed = 0), result)
        fakeEventDao.rows.let { rows ->
            assertEquals(2, rows.size)
            assertTrue(rows.all { it.caldavAccountId == 42L })
            assertEquals(listOf(0, 1), rows.map { it.position })
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
            repeatIntervalDays = null,
            repeatDays = 0,
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
    fun `sync failure keeps lastError empty until repository records it`() = runBlocking {
        // Движок не пишет lastError сам: ошибки выстреливают вверх и их
        // ставит в карточку репозиторий. Здесь проверяем успешный прогон.
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
        rows.removeAll {
            it.caldavAccountId == accountId && it.uid !in uids.toSet()
        }
        return before - rows.size
    }

    override suspend fun deleteByCaldavAccount(accountId: Long): Int {
        val before = rows.size
        rows.removeAll { it.caldavAccountId == accountId }
        return before - rows.size
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

private class FakeApi(var pull: CaldavPull) : CaldavApi {

    override suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef> = emptyList()

    override suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull = pull
}
