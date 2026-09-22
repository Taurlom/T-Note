package com.example.timemanager.domain.usecase

import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.domain.repository.ScheduledEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Все даты, помеченные событием «Выходной» (ISO-ключи). Запрашиваются
 * глобально, а не по месяцу: день из соседнего месяца виден в сетке
 * текущего, и подсветка выходного должна сохраняться с обеих сторон.
 */
class GetWeekendDatesUseCase @Inject constructor(
    private val repository: ScheduledEventRepository
) {
    operator fun invoke(): Flow<Set<String>> =
        repository.getByType(ScheduledEventType.WEEKEND)
            .map { events -> events.map { it.date }.toSet() }
}
