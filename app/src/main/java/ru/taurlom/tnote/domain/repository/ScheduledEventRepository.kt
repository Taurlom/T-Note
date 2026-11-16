package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.time.YearMonth

interface ScheduledEventRepository {

    /**
     * События, пересекающиеся с месяцем: много-дневные попадают в каждый
     * месяц своего интервала, включая промежуточный. Повторы и дни
     * рождения — через [getByType] (якорь не описывает их набор вхождений).
     */
    fun getByMonth(month: YearMonth): Flow<List<ScheduledEvent>>

    fun getByType(type: ScheduledEventType): Flow<List<ScheduledEvent>>

    suspend fun getById(id: Long): ScheduledEvent?

    /** Максимальная позиция среди событий дня — для вставки «в конец». */
    suspend fun getMaxPosition(date: String): Int

    /** @return id вставленной записи. */
    suspend fun add(event: ScheduledEvent): Long
    suspend fun update(event: ScheduledEvent)
    suspend fun delete(event: ScheduledEvent)
}
