package ru.taurlom.tnote.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Пометка «событие удалено в приложении, сервер ещё не знает» (этап 2,
 * двусторонний обмен). Между локальным удалением и DELETE на сервере
 * проходит любая синхронизация: без tombstones pull вписал бы объект
 * обратно — удаление воскресало бы каждую вычитку. Строки стеряются,
 * когда сервер подтвердил DELETE (204) или сообщил, что объекта и так
 * нет (404); при отключении аккаунта очередь гасится целиком.
 */
@Entity(
    tableName = "caldav_tombstones",
    indices = [Index("accountId")],
)
data class CaldavTombstoneEntity(
    /** RFC-uid удалённого события — глобальный ключ синхронизации. */
    @PrimaryKey
    val uid: String,
    /** Аккаунт, в чьей коллекции лежал объект. */
    val accountId: Long,
    /** Href объекта с сервера (для DELETE); null — объект не публиковался. */
    val href: String? = null,
    /** Последний известный ETag — на будущее; DELETE ходит без предусловий. */
    val etag: String? = null,
    /** Момент удаления (epoch millis). */
    val deletedAt: Long,
)

/**
 * Tombstone для события пользователя, которое сейчас сотрётся: без записи
 * подтянутая вычитка вернёт объект обратно до того, как доедет серверный
 * DELETE. null — событие не принадлежит подписке (и там его не было).
 */
fun ScheduledEventEntity.toTombstone(deletedAt: Long): CaldavTombstoneEntity? = caldavAccountId?.let {
    CaldavTombstoneEntity(
        uid = uid,
        accountId = it,
        href = serverHref,
        etag = etag,
        deletedAt = deletedAt,
    )
}
