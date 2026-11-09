package ru.taurlom.tnote.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity
import ru.taurlom.tnote.data.local.entity.toTombstone
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.data.sync.CaldavSyncScheduler
import ru.taurlom.tnote.domain.model.CalendarNote
import ru.taurlom.tnote.domain.repository.CalendarRepository
import ru.taurlom.tnote.domain.repository.PastCalendarCleanup
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val noteDao: CalendarNoteDao,
    private val eventDao: ScheduledEventDao,
    private val tombstoneDao: CaldavTombstoneDao,
    private val syncScheduler: CaldavSyncScheduler,
    private val clock: Clock,
) : CalendarRepository {

    override fun getNotesByMonthPrefix(monthPrefix: String): Flow<List<CalendarNote>> =
        noteDao.getByMonthPrefix(monthPrefix).map { list -> list.map { it.toDomain() } }

    override suspend fun saveNote(note: CalendarNote) {
        // Правка сохраняет момент создания и двигает момент изменения —
        // будущему разрешению конфликтов нужен честный порядок правок.
        // У строк старых версий время неизвестно (0): фиксируем момент
        // первой известной правки.
        val existing = noteDao.getByDateOnce(note.date)
        val now = clock.millis()
        noteDao.insert(
            note.toEntity().copy(
                createdAt = existing?.createdAt?.takeIf { it > 0 } ?: now,
                modifiedAt = now,
            ),
        )
    }

    /**
     * Три DAO в одной транзакции: день не должен остаться очищенным
     * наполовину (заметка стёрта, события остались — или наоборот).
     * Импортированные события уходят и через tombstone-очередь: иначе
     * подтянутая вычитка вернёт их до того, как доедет серверный DELETE.
     */
    override suspend fun deleteDayData(date: String) {
        val tombstones = eventDao.getByDateOnce(date).mapNotNull { it.toTombstone(clock.millis()) }
        database.withTransaction {
            tombstones.forEach { tombstoneDao.insert(it) }
            noteDao.deleteByDate(date)
            eventDao.deleteByDate(date)
        }
        if (tombstones.isNotEmpty()) syncScheduler.requestPush()
    }

    override suspend fun clearCalendar() {
        val tombstones = eventDao.getAttributed().mapNotNull { it.toTombstone(clock.millis()) }
        database.withTransaction {
            tombstones.forEach { tombstoneDao.insert(it) }
            noteDao.deleteAll()
            eventDao.deleteAll()
        }
        if (tombstones.isNotEmpty()) syncScheduler.requestPush()
    }

    /**
     * Чистка прошедшего за одну транзакцию: отрывные заметки уходят все
     * (строка «yyyy-MM-dd» сравнивается лексикографически — безопасно),
     * события — только без будущих вхождений: правило сидит в модели
     * ([ru.taurlom.tnote.domain.model.ScheduledEvent.isOverBefore]), чтобы
     * дни рождения и живые повторения в список кандидатов не попадали.
     * Убираемые события подписки доезжают до сервера через tombstones.
     */
    override suspend fun deletePast(): PastCalendarCleanup {
        val cutoffDate = LocalDate.now()
        val cutoff = cutoffDate.toString()

        var tombstones: List<CaldavTombstoneEntity> = emptyList()
        val cleanup = database.withTransaction {
            val notes = noteDao.deleteBefore(cutoff)
            val removable = eventDao
                .getByDateBefore(cutoff)
                .map { it.toDomain() }
                .filter { it.isOverBefore(cutoffDate) }
            val entities = removable.map { it.toEntity() }
            tombstones = entities.mapNotNull { it.toTombstone(clock.millis()) }
            tombstones.forEach { tombstoneDao.insert(it) }
            eventDao.delete(entities)
            PastCalendarCleanup(events = removable.size, notes = notes)
        }
        if (tombstones.isNotEmpty()) syncScheduler.requestPush()
        return cleanup
    }
}
