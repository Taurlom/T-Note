package ru.taurlom.tnote.domain.usecase

import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * События конкретной даты — основа уведомления-сводки дня.
 *
 * Семантика вхождений — как в сетке календаря
 * (CalendarViewModel.expandEventsForMonth): обычные события берутся по
 * точной дате, день рождения — по эффективной дате нужного года (29
 * февраля в невисокосный год сдвигается на 28-е), повторяющиеся —
 * [ScheduledEvent.occursOn]. «Выходные» не попадают: пометка дня —
 * не повод будить пользователя.
 *
 * Повторяющиеся и дни рождения приходят глобальными запросами по типу:
 * месячный LIKE по дате якоря нашёл бы только якорное вхождение (для
 * дня рождения — только вхождение якорного года).
 */
class GetEventsForDateUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    /**
     * @param today настоящий «сегодня» — для повтора с «удалять прошедшие»
     * (скрыты дни ДО сегодняшнего; само сегодняшнее вхождение видно).
     * Отдельным параметром, а не LocalDate.now(): тесты подставляют своё.
     */
    suspend operator fun invoke(
        date: LocalDate,
        today: LocalDate = LocalDate.now()
    ): List<ScheduledEvent> {
        // Ключи и префикс даты не зависят от локали — как в календаре.
        val monthPrefix = String.format(Locale.US, "%04d-%02d%%", date.year, date.monthValue)
        val dateKey = date.toString()
        val result = repository.getByMonthPrefix(monthPrefix).first()
            .filterTo(mutableListOf()) {
                it.date == dateKey &&
                    it.type != ScheduledEventType.WEEKEND &&
                    it.type != ScheduledEventType.REPEATING
            }
        repository.getByType(ScheduledEventType.BIRTHDAY).first().forEach { birthday ->
            val anchor = birthday.dateOrNull() ?: return@forEach
            // Вхождение якорного года уже пришло из месячного запроса.
            if (anchor.year == date.year) return@forEach
            val (month, day) = ScheduledEvent.effectiveBirthdayDate(anchor, date.year)
            if (month == date.monthValue && day == date.dayOfMonth) {
                result += birthday.copy(date = dateKey)
            }
        }
        repository.getByType(ScheduledEventType.REPEATING).first().forEach { event ->
            if (event.occursOn(date) && !(event.hidePastOccurrences && date.isBefore(today))) {
                result += event.copy(date = dateKey)
            }
        }
        // Тот же порядок, что в списке событий дня в календаре.
        return result.sortedWith(compareBy({ it.position }, { it.id }))
    }
}
