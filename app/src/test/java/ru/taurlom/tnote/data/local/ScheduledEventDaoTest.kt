package ru.taurlom.tnote.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.taurlom.tnote.data.local.entity.ScheduledEventEntity

/**
 * Отключение календаря с сохранением событий: отвязка оставляет строки
 * в приложении, стирая только серверную привязку (аккаунт, href, ETag)
 * и очередь отправки. Строки других источников не трогаются, UID
 * переживает — повторная вычитка сопоставит их по UID без дублей.
 */
@RunWith(RobolectricTestRunner::class)
class ScheduledEventDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ScheduledEventDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.scheduledEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Строка с припиской подписке: серверная привязка заполнена. */
    private fun row(uid: String, accountId: Long? = 1L, remoteImported: Boolean = false, dirty: Boolean = false) = ScheduledEventEntity(
        uid = uid,
        eventDate = "2026-03-01",
        title = "Событие $uid",
        type = "REGULAR",
        icon = "NOTE",
        colorArgb = 0,
        repeatUnit = null,
        repeatInterval = 1,
        repeatWeekdays = null,
        repeatUntil = null,
        hidePast = false,
        position = 0,
        caldavAccountId = accountId,
        remoteImported = remoteImported,
        dirty = dirty,
        etag = accountId?.let { "\"etag-$uid\"" },
        serverHref = accountId?.let { "/calendars/$uid.ics" },
    )

    @Test
    fun `detach keeps rows and clears server linkage`() = runBlocking {
        dao.insert(row("own", remoteImported = false, dirty = true))
        dao.insert(row("imported", remoteImported = true))
        dao.insert(row("other-account", accountId = 2L))
        dao.insert(row("local", accountId = null))

        assertEquals(2, dao.detachFromAccount(1L))

        // Обе строки аккаунта остались, привязка и очередь стёрты, uid цел.
        dao.getByUid(1L, "own")!!.let { kept ->
            assertNull(kept.caldavAccountId)
            assertNull(kept.serverHref)
            assertNull(kept.etag)
            assertFalse(kept.dirty)
            assertEquals("own", kept.uid)
            assertEquals("Событие own", kept.title)
        }
        dao.getByUid(1L, "imported")!!.let { kept ->
            assertNull(kept.caldavAccountId)
            assertNull(kept.serverHref)
            // Бейдж источника остаётся честным: содержимое приехало вычиткой.
            assertTrue(kept.remoteImported)
        }
        // Чужие строки не тронуты: у другой подписки привязка на месте.
        dao.getByUid(2L, "other-account")!!.let { untouched ->
            assertEquals(2L, untouched.caldavAccountId)
            assertEquals("/calendars/other-account.ics", untouched.serverHref)
        }
        // Ничьи строки не пострадали.
        assertNull(dao.getByUid(1L, "local")!!.caldavAccountId)
    }

    @Test
    fun `delete by account removes only that account rows`() = runBlocking {
        dao.insert(row("own"))
        dao.insert(row("other-account", accountId = 2L))
        dao.insert(row("local", accountId = null))

        assertEquals(1, dao.deleteByCaldavAccount(1L))
        assertNull(dao.getByUid(1L, "own"))
        assertEquals("other-account", dao.getByUid(2L, "other-account")!!.uid)
        assertEquals("local", dao.getByUid(1L, "local")!!.uid)
    }

    /**
     * Баг 18 (этап 2): уникальность UID живёт внутри подписки. Один UID с
     * двух серверов — две строки базы, и REPLACE при повторной вычитке
     * переписывает строку своей подписки, не касаясь чужой.
     */
    @Test
    fun `same uid in two accounts coexists as distinct rows`() = runBlocking {
        dao.insert(row("shared", accountId = 1L).copy(title = "Своя копия"))
        dao.insert(row("shared", accountId = 2L).copy(title = "Чужая копия"))

        assertEquals("Своя копия", dao.getByUid(1L, "shared")!!.title)
        assertEquals("Чужая копия", dao.getByUid(2L, "shared")!!.title)

        // Перезапись своей подписки — чужой строки не касается.
        dao.insert(row("shared", accountId = 1L).copy(title = "Обновлённая своя"))
        assertEquals("Обновлённая своя", dao.getByUid(1L, "shared")!!.title)
        assertEquals("Чужая копия", dao.getByUid(2L, "shared")!!.title)
        assertEquals(2, dao.getByMonth("2026-03-01", "2026-04-01").first().size)
    }

    /**
     * Месячная выборка — пересечение интервала [monthStart, nextMonthStart),
     * а не LIKE по датам: «30 января → 2 марта» обязано быть видно и в
     * феврале, где ни начало, ни конец не имеют префикса месяца.
     */
    @Test
    fun `month window selects events spanning interior months`() = runBlocking {
        dao.insert(row("span").copy(eventDate = "2026-01-30", endDate = "2026-03-02"))
        dao.insert(row("first").copy(eventDate = "2026-01-15"))
        dao.insert(row("last").copy(eventDate = "2026-03-05"))
        dao.insert(row("ends-jan").copy(eventDate = "2026-01-10", endDate = "2026-01-31"))
        dao.insert(row("touches-start").copy(eventDate = "2026-02-01"))
        dao.insert(row("touches-end").copy(eventDate = "2026-02-28", endDate = "2026-03-01"))
        dao.insert(row("starts-next").copy(eventDate = "2026-03-01"))

        // Промежуточный месяц длинного события, свои даты и края окна:
        // endDate — последний день включительно, eventDate следующего
        // месяца — уже нет.
        assertEquals(
            setOf("span", "touches-start", "touches-end"),
            dao.getByMonth("2026-02-01", "2026-03-01").first().map { it.uid }.toSet(),
        )

        // Крайние месяцы того же интервала тоже видят событие.
        val january = dao.getByMonth("2026-01-01", "2026-02-01").first().map { it.uid }.toSet()
        assertTrue(january.containsAll(setOf("span", "first", "ends-jan")))
        val march = dao.getByMonth("2026-03-01", "2026-04-01").first().map { it.uid }.toSet()
        assertTrue(march.containsAll(setOf("span", "last", "starts-next", "touches-end")))
    }

    /**
     * Баг 19: diff-удаление не передаёт список снапшота bind'ами — SQLite
     * Android до API 30 отказывается от запросов больше ~999 переменных, а
     * снапшот Google — 2500 объектов страницей. Разница считается в памяти,
     * удаление идёт чанками по 500 id: здесь 1200 строк, из них обречены
     * 600 (пересекают обе границы чанка), dirty-строка вне снапшота
     * выживает.
     */
    @Test
    fun `diff delete survives snapshots beyond sqlite bind limit`() = runBlocking {
        repeat(1200) { i -> dao.insert(row("u$i", dirty = i == 1)) }

        val keep = (0 until 1200 step 2).map { "u$it" }
        val removed = dao.deleteNotInUids(1L, keep)

        assertEquals(599, removed)
        val left = dao.getByMonth("2026-03-01", "2026-04-01").first().map { it.uid }.toSet()
        assertEquals(601, left.size)
        // Границы чанков: первый удалённый нечётный и последний нечётный
        // ушли, сохранённые чётные и единственная dirty — на месте.
        assertFalse("u3" in left)
        assertFalse("u1199" in left)
        assertTrue("u1" in left)
        assertTrue("u1000" in left)
    }
}
