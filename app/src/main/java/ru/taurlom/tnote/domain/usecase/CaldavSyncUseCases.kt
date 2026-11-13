package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavProvider
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.repository.CaldavRepository
import javax.inject.Inject

/**
 * Поиск календарей с событиями на сервере выбранного провайдера —
 * первый шаг формы «Подключение календаря».
 */
class DiscoverCaldavCalendarsUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(provider: CaldavProvider, server: String, username: String, password: String): List<CaldavCalendarRef> =
        repository.discover(provider, server, username, password)
}

/**
 * Подключение выбранного календаря: аккаунт сохраняется, секрет — в
 * защищённое хранилище (для Google — заглушка, токен берётся OAuth'ом),
 * сразу запускается первая синхронизация.
 */
class ConnectCaldavAccountUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(
        provider: CaldavProvider,
        server: String,
        username: String,
        password: String,
        calendar: CaldavCalendarRef,
    ): Long = repository.connect(provider, server, username, password, calendar)
}

/**
 * Отключение аккаунта. С [keepEvents] события этого источника остаются
 * в приложении как локальные события, иначе удаляются вместе с аккаунтом
 * (на сервере они остаются в обоих случаях).
 */
class DisconnectCaldavAccountUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(accountId: Long, keepEvents: Boolean) = repository.disconnect(accountId, keepEvents)
}

/** Ручная синхронизация одного аккаунта — кнопка «Обновить». */
class SyncCaldavAccountUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(accountId: Long): CaldavSyncResult = repository.sync(accountId)
}

/** Живой список подключённых аккаунтов для экрана настроек. */
class ObserveCaldavAccountsUseCase @Inject constructor(private val repository: CaldavRepository) {
    operator fun invoke(): Flow<List<CaldavAccount>> = repository.observeAccounts()
}

/** Живое число локальных событий без приписки — показывать ли кнопку «залить старые». */
class ObserveLocalOnlyEventsCountUseCase @Inject constructor(private val repository: CaldavRepository) {
    operator fun invoke(): Flow<Int> = repository.observeLocalOnlyCount()
}

/**
 * «Залить старые события в календарь»: неприписанные события (созданные
 * до подключения подписки) приписываются ей и ставятся в очередь отправки.
 */
class PublishLocalEventsUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(accountId: Long): Int = repository.publishLocalOnly(accountId)
}
