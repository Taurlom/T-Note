package ru.taurlom.tnote.data.sync

import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import ru.taurlom.tnote.data.local.entity.CaldavTombstoneEntity
import ru.taurlom.tnote.data.local.entity.CalendarNoteEntity
import ru.taurlom.tnote.domain.model.CalendarNote
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Мост «заметка дня ↔ iCalendar (VEVENT)» — перенос заметок через Яндекса.
 * Сущности «заметка» в CalDAV нет, поэтому заметка едет событием-носителем:
 * имя «✍️ [NOTE_TITLE]» — маркер заметки (договорённость переноса), текст —
 * в DESCRIPTION. UID детерминированный «note-<дата>@taurlom»: заметка одна
 * на день (первичный ключ calendar_notes — дата), и повторная публикация
 * правки того же дня переписывает свой объект, а не плодит дубликаты.
 *
 * Содержимое не выражается моделью событий (ни RRULE, ни X-TNOTE-*), так
 * что приёмники вне T-Note видят обычное дневное событие с описанием.
 * Свой PUT уходит с UTF-8 BOM (CaldavHttpClient) — сервер хранит и ✍️,
 * и кириллицу текста дословно; тела без BOM чинятся при разборе
 * (repairMojibake) так же, как имена событий.
 */
private const val NOTE_ICON = "✍"
private const val NOTE_TITLE = "Заметка"

/** Имя события-носителя заметки на границе ICS. */
const val NOTE_SUMMARY = "$NOTE_ICON\uFE0F $NOTE_TITLE"

private val icsDate = DateTimeFormatter.BASIC_ISO_DATE

/** RFC-uid серверного объекта заметки дня [date] («yyyy-MM-dd»). */
fun noteUidFor(date: String): String = "note-$date@taurlom"

/**
 * Является ли событие заметкой: маркер — символ ✍ (U+270D) в начале имени.
 * Разбор идёт по уже починенному имени (repairMojibake в toScheduledEvent),
 * вариант селектора U+FE0F после символа роли не играет.
 */
internal fun noteBySummary(title: String): Boolean = title.trimStart().startsWith(NOTE_ICON)

/**
 * Текст заметки из DESCRIPTION (починка кракозябр — как у имён).
 * Свойства DESCRIPTION у объекта может не быть (текст потерян сервером
 * или правился старой версией приложения) — тогда пустая строка.
 */
internal fun VEvent.noteText(): String = repairMojibake(properties.byName(Property.DESCRIPTION)?.value ?: "")

/**
 * VEVENT-носитель заметки: дневная метка (DTSTART/DTEND — соседние дни),
 * имя-маркер, текст в DESCRIPTION, метки времени для LWW («последняя
 * правка побеждает» — тот же механизм, что у событий).
 */
fun CalendarNote.toIcsEvent(): VEvent {
    val anchor = runCatching { LocalDate.parse(date) }.getOrNull()
        ?: throw IllegalArgumentException("Битая дата заметки: $date")
    val stamp = modifiedAt.takeIf { it > 0 } ?: createdAt.takeIf { it > 0 }
    return VEvent().apply {
        properties.add(Uid(noteUidFor(date)))
        // DTSTAMP конструктор ставит сам — его дубль невалиден по RFC 5545,
        // Яндекс на такое тело отвечает 504 (см. toIcsEvent события).
        properties.add(DtStart(net.fortuna.ical4j.model.Date(anchor.format(icsDate), "yyyyMMdd")))
        properties.add(DtEnd(net.fortuna.ical4j.model.Date(anchor.plusDays(1).format(icsDate), "yyyyMMdd")))
        properties.add(Summary(NOTE_SUMMARY))
        properties.add(Description(text))
        createdAt.takeIf { it > 0 }?.let { properties.add(Created(net.fortuna.ical4j.model.DateTime(it))) }
        stamp?.let { properties.add(LastModified(net.fortuna.ical4j.model.DateTime(it))) }
    }
}

/**
 * Tombstone заметки приписанной подписке: сервер должен узнать об удалении
 * заметки так же, как об удалении события. null — заметка локальная
 * (на сервере её не было). Href с сервера важнее выведенного из UID:
 * веб-созданные ✍️-события сервер называет по-своему.
 */
fun CalendarNoteEntity.toNoteTombstone(deletedAt: Long): CaldavTombstoneEntity? = caldavAccountId?.let {
    CaldavTombstoneEntity(
        uid = noteUidFor(eventDate),
        accountId = it,
        href = serverHref,
        etag = etag,
        deletedAt = deletedAt,
    )
}
