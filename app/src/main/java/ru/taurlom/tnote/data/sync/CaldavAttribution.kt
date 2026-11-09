package ru.taurlom.tnote.data.sync

import ru.taurlom.tnote.data.local.entity.CaldavAccountEntity
import ru.taurlom.tnote.domain.model.ScheduledEventType

/**
 * Политика приписки событий подписке (этап 2): что и когда едет на сервер.
 * v1 держит правило простым: событие без приписки (создано в приложении)
 * забирает единственная активная подписка; если их несколько — выбор
 * календаря не угадывается, событие остаётся локальным. Правка уже
 * приписанного события всегда идёт в его коллекцию.
 */
object CaldavAttribution {

    /**
     * Аккаунт, чьей коллекции принадлежит событие: своё (приписанное)
     * остаётся прежним, «безформенное» получает единственную активную
     * подписку. null — остаётся локальным: подписок нет или несколько,
     * а также для «выходного» (генерируемая отметка, границы v1 —
     * наружу не ходит).
     */
    fun targetFor(type: ScheduledEventType, caldavAccountId: Long?, accounts: List<CaldavAccountEntity>): Long? {
        if (type == ScheduledEventType.WEEKEND) return null
        caldavAccountId?.let { return it }
        return accounts.singleOrNull()?.id
    }

    /**
     * Ждёт ли строка отправки после правки: у «выходного» сервер не в цене
     * (он локальная отметка), у остальных — когда событие приписано
     * подписке (было или стало).
     */
    fun isDirty(type: ScheduledEventType, currentAccountId: Long?, targetAccountId: Long?): Boolean =
        type != ScheduledEventType.WEEKEND && (currentAccountId != null || targetAccountId != null)
}
