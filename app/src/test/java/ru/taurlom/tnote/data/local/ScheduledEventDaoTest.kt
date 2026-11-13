package ru.taurlom.tnote.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
        dao.getByUid("own")!!.let { kept ->
            assertNull(kept.caldavAccountId)
            assertNull(kept.serverHref)
            assertNull(kept.etag)
            assertFalse(kept.dirty)
            assertEquals("own", kept.uid)
            assertEquals("Событие own", kept.title)
        }
        dao.getByUid("imported")!!.let { kept ->
            assertNull(kept.caldavAccountId)
            assertNull(kept.serverHref)
            // Бейдж источника остаётся честным: содержимое приехало вычиткой.
            assertTrue(kept.remoteImported)
        }
        // Чужие строки не тронуты: у другой подписки привязка на месте.
        dao.getByUid("other-account")!!.let { untouched ->
            assertEquals(2L, untouched.caldavAccountId)
            assertEquals("/calendars/other-account.ics", untouched.serverHref)
        }
        // Ничьи строки не пострадали.
        assertNull(dao.getByUid("local")!!.caldavAccountId)
    }

    @Test
    fun `delete by account removes only that account rows`() = runBlocking {
        dao.insert(row("own"))
        dao.insert(row("other-account", accountId = 2L))
        dao.insert(row("local", accountId = null))

        assertEquals(1, dao.deleteByCaldavAccount(1L))
        assertNull(dao.getByUid("own"))
        assertEquals("other-account", dao.getByUid("other-account")!!.uid)
        assertEquals("local", dao.getByUid("local")!!.uid)
    }
}
