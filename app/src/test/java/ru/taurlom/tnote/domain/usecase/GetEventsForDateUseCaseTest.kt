package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GetEventsForDateUseCaseTest {

    /** ‘иксированное Ђсегодн€ї Ч Ђудал€ть прошедшиеї считаетс€ от него. */
    private val today: LocalDate = LocalDate.parse("2026-07-10")

    private fun event(
        id: Long,
        date: String,
        type: ScheduledEventType = ScheduledEventType.REGULAR,
        position: Int = 0
    ) = ScheduledEvent(id = id, date = date, title = "—обытие $id", type = type, position = position)

    private fun repeating(
        id: Long,
        anchor: String,
        interval: Int = 3,
        hidePast: Boolean = false
    ) = ScheduledEvent(
        id = id,
        date = anchor,
        title = "ѕовтор $id",
        type = ScheduledEventType.REPEATING,
        repeatIntervalDays = interval,
        hidePastOccurrences = hidePast
    )

    private fun useCase(vararg events: ScheduledEvent) =
        GetEventsForDateUseCase(FakeEventRepository(events.toList()))

    /** Ђ2026-07-10ї > LocalDate: короче, чем LocalDate.parse в каждом тесте. */
    private fun date(iso: String): LocalDate = LocalDate.parse(iso)

    @Test
    fun `regular event on the date is included`() = runBlocking {
        val events = useCase(event(1, "2026-07-10"))(date("2026-07-10"), today)
        assertEquals(listOf(1L), events.map { it.id })
    }

    @Test
    fun `regular event on another date is not included`() = runBlocking {
        val events = useCase(event(1, "2026-07-09"))(date("2026-07-10"), today)
        assertEquals(emptyList<ScheduledEvent>(), events)
    }

    @Test
    fun `weekend marker is not a digest event`() = runBlocking {
        // ѕометка Ђвыходнойї Ч цвет фона дн€, а не событие, ради которого
        // стоит будить владельца.
        val events = useCase(event(1, "2026-07-10", ScheduledEventType.WEEKEND))(date("2026-07-10"), today)
        assertEquals(emptyList<ScheduledEvent>(), events)
    }

    @Test
    fun `birthday occurrence is built from anchor in another year`() = runBlocking {
        // якорь 1994 года не попадает в мес€чную выборку 2026-07 Ч
        // вхождение собирает сам use case.
        val events = useCase(event(1, "1994-07-10", ScheduledEventType.BIRTHDAY))(date("2026-07-10"), today)
        assertEquals(listOf(1L), events.map { it.id })
        // ƒата событи€ подмен€етс€ на дату вхождени€ Ч как в сетке календар€.
        assertEquals("2026-07-10", events.single().date)
    }

    @Test
    fun `birthday in the anchor year comes once - no duplicate`() = runBlocking {
        // ¬хождение €корного года уже в мес€чной выборке; расширение по
        // дн€м рождени€ его пропускает.
        val events = useCase(event(1, "2026-07-10", ScheduledEventType.BIRTHDAY))(date("2026-07-10"), today)
        assertEquals(1, events.size)
    }

    @Test
    fun `leap birthday lands on february 28 in non-leap year`() = runBlocking {
        val events = useCase(event(1, "2000-02-29", ScheduledEventType.BIRTHDAY))(date("2026-02-28"), date("2026-02-28"))
        assertEquals(listOf("2026-02-28"), events.map { it.date })
    }

    @Test
    fun `repeating occurrence is included once`() = runBlocking {
        // якорь 07.07 с интервалом 3 совпадает с 10.07; €корна€ дата
        // приходит и из мес€чного запроса Ч дублировани€ быть не должно.
        val events = useCase(repeating(1, "2026-07-07"))(date("2026-07-10"), today)
        assertEquals(1, events.size)
    }

    @Test
    fun `repeating non-occurrence day is empty`() = runBlocking {
        val events = useCase(repeating(1, "2026-07-07"))(date("2026-07-08"), today)
        assertEquals(emptyList<ScheduledEvent>(), events)
    }

    @Test
    fun `hidden past repeating occurrences are skipped, today is shown`() = runBlocking {
        val events = useCase(repeating(1, "2026-07-04", hidePast = true))
        // 07.07 Ч вхождение в прошлом: скрыто.
        assertEquals(
            emptyList<ScheduledEvent>(),
            events(date("2026-07-07"), today)
        )
        // 07.10 Ч сегодн€шнее вхождение: видно.
        assertEquals(1, events(date("2026-07-10"), today).size)
    }

    @Test
    fun `events are ordered by position`() = runBlocking {
        val events = useCase(
            event(2, "2026-07-10", position = 5),
            event(1, "2026-07-10", position = 1)
        )(date("2026-07-10"), today)
        assertEquals(listOf(1L, 2L), events.map { it.id })
    }

    /** «аглушка хранилища: мес€чный запрос Ч LIKE-префикс, типовой Ч по типу. */
    private class FakeEventRepository(
        private val events: List<ScheduledEvent>
    ) : ScheduledEventRepository {
        override fun getByMonthPrefix(monthPrefix: String): Flow<List<ScheduledEvent>> {
            val prefix = monthPrefix.removeSuffix("%")
            return flowOf(events.filter { it.date.startsWith(prefix) })
        }

        override fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>> =
            flowOf(events.filter { it.type == type })

        override suspend fun getById(id: Long): ScheduledEvent? = null
        override suspend fun add(event: ScheduledEvent): Long = 0
        override suspend fun update(event: ScheduledEvent) = Unit
        override suspend fun delete(event: ScheduledEvent) = Unit
        override suspend fun deleteByDate(date: String) = Unit
        override suspend fun clearAll() = Unit
    }
}
