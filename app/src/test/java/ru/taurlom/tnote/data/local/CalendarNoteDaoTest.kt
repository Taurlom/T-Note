package ru.taurlom.tnote.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity
import java.time.LocalDate

/**
 * Разница вычитки по заметкам: удаление отсутствующих на сервере дней.
 * Ключевые свойства — приписка аккаунта, неприкосновенность dirty-строк
 * и отсутствие bind-списков размера снапшота (баг 19: SQLite Android до
 * API 30 не переживает «eventDate NOT IN (:dates)» на 1000+ значений).
 */
@RunWith(RobolectricTestRunner::class)
class CalendarNoteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CalendarNoteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.calendarNoteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(date: String, accountId: Long? = 1L, dirty: Boolean = false) = CalendarNoteEntity(
        eventDate = date,
        text = "Заметка $date",
        createdAt = 100,
        modifiedAt = 200,
        caldavAccountId = accountId,
        dirty = dirty,
    )

    @Test
    fun `deleteNotInDates keeps dirty, foreign and kept rows`() = runBlocking {
        dao.insert(note("2026-03-01"))
        dao.insert(note("2026-03-02"))
        dao.insert(note("2026-03-03", dirty = true))
        dao.insert(note("2026-03-04", accountId = 2L))
        dao.insert(note("2026-03-05", accountId = null))

        val removed = dao.deleteNotInDates(1L, listOf("2026-03-01"))

        // Из подписки 1 удалился только день 02: 03 — правка в очереди,
        // 04 — чужой аккаунт, 05 — локальная заметка.
        assertEquals(1, removed)
        assertNotNull(dao.getByDateOnce("2026-03-01"))
        assertNull(dao.getByDateOnce("2026-03-02"))
        assertNotNull(dao.getByDateOnce("2026-03-03"))
        assertNotNull(dao.getByDateOnce("2026-03-04"))
        assertNotNull(dao.getByDateOnce("2026-03-05"))
    }

    /**
     * Баг 19: 1200 дней кандидатов — список снапшота давно бы превысил
     * ~999 bind-переменных старого SQLite; composed-удаление считает
     * разницу в памяти и удаляет чанками по 500 id.
     */
    @Test
    fun `note diff delete survives snapshots beyond sqlite bind limit`() = runBlocking {
        val base = LocalDate.of(2021, 1, 1)
        repeat(1200) { i ->
            dao.insert(note(base.plusDays(i.toLong()).toString(), dirty = i == 1))
        }

        val keep = (0 until 1200 step 2).map { base.plusDays(it.toLong()).toString() }
        val removed = dao.deleteNotInDates(1L, keep)

        // Обречены были 600 нечётных, из них один dirty выжил; чанки 500+99
        // пересекли обе границы без «too many SQL variables».
        assertEquals(599, removed)
        assertNotNull(dao.getByDateOnce(base.plusDays(0).toString()))
        assertNotNull(dao.getByDateOnce(base.plusDays(1).toString()))
        assertNull(dao.getByDateOnce(base.plusDays(3).toString()))
        assertNotNull(dao.getByDateOnce(base.plusDays(500).toString()))
        assertNull(dao.getByDateOnce(base.plusDays(501).toString()))
        assertNull(dao.getByDateOnce(base.plusDays(1199).toString()))
    }

    /**
     * Отключение подписки с сохранением стирает и remoteUid (баг 15):
     * чужой серверный uid не должен пережить отвязку и вернуться с
     * другой подпиской.
     */
    @Test
    fun `detach clears carrier bindings including remote uid`() = runBlocking {
        dao.insert(note("2026-03-01").copy(remoteUid = "web-uid", etag = "\"e1\"", serverHref = "/c/n.ics"))

        assertEquals(1, dao.detachFromAccount(1L))

        val detached = dao.getByDateOnce("2026-03-01")!!
        assertNull(detached.caldavAccountId)
        assertNull(detached.remoteUid)
        assertNull(detached.etag)
        assertNull(detached.serverHref)
        assertFalse(detached.dirty)
    }
}
