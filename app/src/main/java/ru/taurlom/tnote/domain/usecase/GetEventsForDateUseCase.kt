package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.first
import ru.taurlom.tnote.domain.model.EventExpander
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * События конкретной даты — основа уведомления-сводки дня.
 *
 * Семантика вхождений — общая с сеткой календаря и живёт в одном месте
 * (EventExpander); сводка добавляет своё правило: «выходные» не
 * попадают — пометка дня не повод будить пользователя.
 *
 * Повторяющиеся и дни рождения приходят глобальными запросами по типу:
 * месячное окно по датам якоря нашло бы только якорное вхождение (для
 * дня рождения — только вхождение якорного года). Обычные события —
 * окном месяца с перекрытием интервала: «30 января → 2 марта» видно и
 * в февральской сводке.
 */
class GetEventsForDateUseCase @Inject constructor(private val repository: ScheduledEventRepository) {
    /**
     * @param today настоящий «сегодня» — для повтора с «удалять прошедшие»
     * (скрыты дни ДО сегодняшнего; само сегодняшнее вхождение видно).
     * Отдельным параметром, а не LocalDate.now(): тесты подставляют своё.
     */
    suspend operator fun invoke(date: LocalDate, today: LocalDate = LocalDate.now()): List<ScheduledEvent> = EventExpander.forDate(
        monthEvents = repository.getByMonth(YearMonth.from(date)).first(),
        birthdays = repository.getByType(ScheduledEventType.BIRTHDAY).first(),
        repeating = repository.getByType(ScheduledEventType.REPEATING).first(),
        date = date,
        today = today,
    ).filterNot { it.type == ScheduledEventType.WEEKEND }
}
