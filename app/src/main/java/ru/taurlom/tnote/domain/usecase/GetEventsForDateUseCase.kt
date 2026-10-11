package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.first
import ru.taurlom.tnote.domain.model.EventExpander
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * События конкретной даты — основа уведомления-сводки дня.
 *
 * Семантика вхождений — общая с сеткой календаря и живёт в одном месте
 * (EventExpander); сводка добавляет своё правило: «выходные» не
 * попадают — пометка дня не повод будить пользователя.
 *
 * Повторяющиеся и дни рождения приходят глобальными запросами по типу:
 * месячный LIKE по дате якоря нашёл бы только якорное вхождение (для
 * дня рождения — только вхождение якорного года).
 */
class GetEventsForDateUseCase @Inject constructor(private val repository: ScheduledEventRepository) {
    /**
     * @param today настоящий «сегодня» — для повтора с «удалять прошедшие»
     * (скрыты дни ДО сегодняшнего; само сегодняшнее вхождение видно).
     * Отдельным параметром, а не LocalDate.now(): тесты подставляют своё.
     */
    suspend operator fun invoke(date: LocalDate, today: LocalDate = LocalDate.now()): List<ScheduledEvent> {
        // Ключи и префикс даты не зависят от локали — как в календаре.
        val monthPrefix = String.format(Locale.US, "%04d-%02d%%", date.year, date.monthValue)
        return EventExpander.forDate(
            monthEvents = repository.getByMonthPrefix(monthPrefix).first(),
            birthdays = repository.getByType(ScheduledEventType.BIRTHDAY).first(),
            repeating = repository.getByType(ScheduledEventType.REPEATING).first(),
            date = date,
            today = today,
        ).filterNot { it.type == ScheduledEventType.WEEKEND }
    }
}
