package ru.taurlom.tnote.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.local.CaldavDao
import ru.taurlom.tnote.data.local.CaldavTombstoneDao
import ru.taurlom.tnote.data.local.CalendarNoteDao
import ru.taurlom.tnote.data.local.ScheduledEventDao
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity
import ru.taurlom.tnote.data.local.entity.toTombstone
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.data.sync.CaldavSyncScheduler
import ru.taurlom.tnote.data.sync.noteUidFor
import ru.taurlom.tnote.data.sync.toNoteTombstone
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
    private val caldavDao: CaldavDao,
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
        // Этап 2: заметка едет в Яндекса событием-носителем — приписка
        // единственной подписке, как у событий (CaldavAttribution); правка
        // приписанной остаётся в её коллекции. ETag/href переживают правку:
        // без них каждая правка уходила бы первой публикацией.
        val accountId = existing?.caldavAccountId ?: caldavDao.getAll().singleOrNull()?.id
        database.withTransaction {
            noteDao.insert(
                note.toEntity().copy(
                    createdAt = existing?.createdAt?.takeIf { it > 0 } ?: now,
                    modifiedAt = now,
                    caldavAccountId = accountId,
                    dirty = accountId != null,
                    // Правка двигает счётчик локальных мутаций — по нему движок
                    // подтверждает PUT (см. CalendarNoteDao.markPushed).
                    localRevision = (existing?.localRevision ?: 0L) + 1,
                    etag = existing?.etag,
                    serverHref = existing?.serverHref,
                ),
            )
            // Пересоздание заметки гасит её очередь удаления, той же
            // транзакцией: UID носителя выводится из даты (noteUidFor), так
            // что «удалить день, потом написать заново» — одна и та же
            // идентичность на сервере. Оставленный tombstone снёс бы
            // только что опубликованный объект в том же прогоне (PUT идёт
            // раньше DELETE), а следующая вычитка вычистила бы и локальную
            // строку как отсутствующую на сервере — заметка терялась бы
            // совсем. Удаление локально всегда старше пересоздания, поэтому
            // здесь не нужно сравнивать ревизии: живая строка важнее
            // tombstone.
            if (accountId != null) {
                tombstoneDao.deleteForIdentity(noteUidFor(note.date), accountId)
            }
        }
        if (accountId != null) syncScheduler.requestPush()
    }

    /**
     * Три DAO в одной транзакции: день не должен остаться очищенным
     * наполовину (заметка стёрта, события остались — или наоборот).
     * Импортированные события и приписанные заметки уходят и через
     * tombstone-очередь: иначе подтянутая вычитка вернёт их до того,
     * как доедет серверный DELETE.
     *
     * Кандидатов на удаление читаем ВНУТРИ транзакции (как [deletePast]):
     * вычитка, вписавшая строку этого дня между чтением и удалением,
     * осталась бы без tombstone — её объект живёт на сервере, и следующий
     * pull вернул бы удалившийся день обратно. Мьютекс синхронизации здесь
     * не бережёт: он сериализует прогоны движка и lifecycle-действия
     * подписки (CaldavRepositoryImpl.lifecycleLock), а пользовательское
     * удаление к ним не относится.
     */
    override suspend fun deleteDayData(date: String) {
        var tombstones: List<CaldavTombstoneEntity> = emptyList()
        database.withTransaction {
            tombstones = eventDao.getByDateOnce(date).mapNotNull { it.toTombstone(clock.millis()) } +
                listOfNotNull(noteDao.getByDateOnce(date)?.toNoteTombstone(clock.millis()))
            tombstones.forEach { tombstoneDao.insert(it) }
            noteDao.deleteByDate(date)
            eventDao.deleteByDate(date)
        }
        if (tombstones.isNotEmpty()) syncScheduler.requestPush()
    }

    /**
     * Чистка календаря — та же транзакция на чтение и удаление: список
     * «что убираем» берётся целиком внутри, иначе событие или заметка,
     * подтянутые вычиткой в промежутке, были бы стёрты без очереди
     * удалений и воскресли бы следующим прогоном.
     */
    override suspend fun clearCalendar() {
        var tombstones: List<CaldavTombstoneEntity> = emptyList()
        database.withTransaction {
            tombstones = eventDao.getAttributed().mapNotNull { it.toTombstone(clock.millis()) } +
                noteDao.getAttributed().mapNotNull { it.toNoteTombstone(clock.millis()) }
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
            // Tombstones собираются до удаления: приписанные заметки дня
            // должны доехать до сервера тем же DELETE-очередём.
            val staleNotes = noteDao.getAttributedBefore(cutoff)
            val notes = noteDao.deleteBefore(cutoff)
            val removable = eventDao
                .getByDateBefore(cutoff)
                .map { it.toDomain() }
                .filter { it.isOverBefore(cutoffDate) }
            val entities = removable.map { it.toEntity() }
            tombstones = entities.mapNotNull { it.toTombstone(clock.millis()) } +
                staleNotes.mapNotNull { it.toNoteTombstone(clock.millis()) }
            tombstones.forEach { tombstoneDao.insert(it) }
            eventDao.delete(entities)
            PastCalendarCleanup(events = removable.size, notes = notes)
        }
        if (tombstones.isNotEmpty()) syncScheduler.requestPush()
        return cleanup
    }
}
