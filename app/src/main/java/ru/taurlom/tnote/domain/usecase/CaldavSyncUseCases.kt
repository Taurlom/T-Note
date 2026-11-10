package ru.taurlom.tnote.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.CaldavAccount
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.CaldavSyncResult
import ru.taurlom.tnote.domain.repository.CaldavRepository
import javax.inject.Inject

/**
 * Поиск календарей с событиями (VEVENT) на CalDAV-сервере —
 * первый шаг формы «Подключение календаря».
 */
class DiscoverCaldavCalendarsUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(server: String, username: String, password: String): List<CaldavCalendarRef> =
        repository.discover(server, username, password)
}

/**
 * Подключение выбранного календаря: аккаунт сохраняется, пароль — в
 * защищённое хранилище, сразу запускается первая синхронизация.
 */
class ConnectCaldavAccountUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(server: String, username: String, password: String, calendar: CaldavCalendarRef): Long =
        repository.connect(server, username, password, calendar)
}

/**
 * Отключение аккаунта: события этого источника удаляются из приложения
 * вместе с аккаунтом (на сервере они остаются).
 */
class DisconnectCaldavAccountUseCase @Inject constructor(private val repository: CaldavRepository) {
    suspend operator fun invoke(accountId: Long) = repository.disconnect(accountId)
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
