package ru.taurlom.tnote.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.domain.model.ScheduledEventType

/**
 * Политика приписки (этап 2): единственная подписка забирает события без
 * приписки, WEEKEND всегда остаётся локальным, а при нескольких подписках
 * календарь-получатель не угадывается.
 */
class CaldavAttributionTest {

    private fun account(id: Long) = CaldavAccountEntity(
        id = id,
        serverUrl = "https://caldav.yandex.ru",
        username = "taur-lom@yandex.ru",
        collectionUrl = "https://caldav.yandex.ru/calendars/taur-lom%40yandex.ru/events-$id/",
        collectionName = "T-Note",
    )

    @Test
    fun `sole subscription adopts unattributed event`() {
        assertEquals(7L, CaldavAttribution.targetFor(ScheduledEventType.REGULAR, null, listOf(account(7))))
    }

    @Test
    fun `multiple subscriptions leave event local`() {
        assertNull(CaldavAttribution.targetFor(ScheduledEventType.REGULAR, null, listOf(account(7), account(8))))
    }

    @Test
    fun `no subscriptions leave event local`() {
        assertNull(CaldavAttribution.targetFor(ScheduledEventType.REGULAR, null, emptyList()))
    }

    @Test
    fun `attributed event keeps its subscription`() {
        assertEquals(3L, CaldavAttribution.targetFor(ScheduledEventType.REGULAR, 3L, listOf(account(7))))
    }

    @Test
    fun `weekend never travels to server`() {
        assertNull(CaldavAttribution.targetFor(ScheduledEventType.WEEKEND, null, listOf(account(7))))
        assertNull(CaldavAttribution.targetFor(ScheduledEventType.WEEKEND, 3L, listOf(account(7))))
    }

    @Test
    fun `dirty marks only attributed regular rows`() {
        assertFalse(CaldavAttribution.isDirty(ScheduledEventType.REGULAR, null, null))
        assertTrue(CaldavAttribution.isDirty(ScheduledEventType.REGULAR, 7L, 7L))
        assertTrue(CaldavAttribution.isDirty(ScheduledEventType.REGULAR, null, 7L))
        // «Выходной» не ставится в очередь, даже если строка была приписана.
        assertFalse(CaldavAttribution.isDirty(ScheduledEventType.WEEKEND, 7L, null))
    }
}
