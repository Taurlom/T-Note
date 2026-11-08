package ru.taurlom.tnote.data.sync

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Version
import net.fortuna.ical4j.model.property.XProperty
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val PROP_TYPE = "X-TNOTE-TYPE"
private const val PROP_ICON = "X-TNOTE-ICON"
private const val PROP_COLOR = "X-TNOTE-COLOR"
private const val PROP_HIDE_PAST = "X-TNOTE-HIDEPAST"

/** PRODID обёртки VCALENDAR, которую отдаёт приложение при PUT. */
private const val PROD_ID = "-//T-Note//iCalendar 1.0//RU"

private val icsDate = DateTimeFormatter.BASIC_ISO_DATE

/**
 * Мост «событие T-Note ↔ iCalendar (VEVENT)» — фундамент CalDAV-синхронизации:
 * свои события экспортируются как VEVENT (CalDAV PUT), серверные разбираются
 * в доменную модель (REPORT/multiget). Наши события — дневные метки без времени,
 * поэтому обмен договорён так:
 *
 *  - DTSTART — VALUE=DATE (дневная метка), DTEND — следующий день (эксклюзивный
 *    по RFC 5545). Чужое событие с точным временем импортируется по календарному
 *    дню, время суток отбрасывается: внутри дня планировщик ничего не рисует;
 *  - [ScheduledEvent.position] — локальная сортировка, в ICS не экспортируется:
 *    очередность восстанавливается из локальной копии, сопоставленной по UID,
 *    а не с сервера;
 *  - цвет, иконка, тип и «прошлые вхождения скрыты» — нестандартные свойства
 *    X-TNOTE-*: сервер хранит их дословно, другие календарные клиенты игнорируют;
 *  - повторение: RRULE FREQ=DAILY;INTERVAL=N и COUNT=M — прямое отображение на
 *    наш якорь [ScheduledEvent.date], интервал и окно [ScheduledEvent.repeatDays];
 *    окно без ограничения (repeatDays = 0) экспортируется без COUNT;
 *  - день рождения: RRULE FREQ=YEARLY от полного якоря с годом; февральские 29
 *    и високосный год сводит модель (effectiveBirthdayDate), ICS не знает о них;
 *  - «выходной» (WEEKEND) — генерируемая отметка: наружу не экспортируется,
 *    на входе трактуется как REGULAR.
 *
 * Непредставимые своей моделью чужие RRULE (WEEKLY, BYMONTH и прочие правила)
 * разбираются как REGULAR на якорный день; честное разворачивание остальных
 * вхождений на срок вперед — работа движка синхронизации (вместе с EXDATE и
 * RECURRENCE-ID), это заготовка, а не финал.
 *
 * VEVENT для CalDAV PUT. Битая якорная дата — исключение: движок перед
 * экспортом фильтрует строки, события с «2026-13-40» до PUT не доходят.
 */
fun ScheduledEvent.toIcsEvent(): VEvent {
    val anchor = dateOrNull()
        ?: throw IllegalArgumentException("Битая якорная дата события: $date")
    // Явное строковое имя: у VEvent есть собственное свойство uid (ical4j 3.2),
    // оно затеняет ScheduledEvent.uid внутри лямбды apply.
    val ownUid: String = this.uid
    // «Выходной» — локальная генерируемая отметка: наружу уходит как
    // обычное событие, чтобы приёмники с USER-WEEKEND не мучились.
    val exportType = if (type == ScheduledEventType.WEEKEND) {
        ScheduledEventType.REGULAR
    } else {
        type
    }
    return VEvent().apply {
        properties.add(Uid(ownUid))
        properties.add(DtStamp())
        properties.add(DtStart(Date(anchor.format(icsDate), "yyyyMMdd")))
        properties.add(DtEnd(Date(anchor.plusDays(1).format(icsDate), "yyyyMMdd")))
        properties.add(Summary(title))
        // RRULE пишут повторения: «выходной» наружу вообще не ходит,
        // и повторение без интервала повторить нечего — идёт без RRULE.
        if (type == ScheduledEventType.REPEATING) {
            repeatIntervalDays?.takeIf { it > 0 }?.let { interval ->
                val value = if (repeatDays > 0) {
                    "FREQ=DAILY;INTERVAL=$interval;COUNT=$repeatDays"
                } else {
                    "FREQ=DAILY;INTERVAL=$interval"
                }
                properties.add(RRule(value))
            }
        }
        if (type == ScheduledEventType.BIRTHDAY) {
            properties.add(RRule("FREQ=YEARLY"))
        }
        properties.add(XProperty(PROP_TYPE, exportType.name))
        properties.add(XProperty(PROP_ICON, icon.name))
        if (colorArgb != ScheduledEvent.DEFAULT_COLOR) {
            properties.add(XProperty(PROP_COLOR, colorArgb.toString()))
        }
        if (hidePastOccurrences) {
            properties.add(XProperty(PROP_HIDE_PAST, "1"))
        }
    }
}

/** VCALENDAR с одним или несколькими VEVENT — тело CalDAV PUT. */
fun eventCalendar(events: List<VEvent>): Calendar = Calendar().apply {
    properties.add(ProdId(PROD_ID))
    properties.add(Version.VERSION_2_0)
    components.addAll(events)
}

/** ICS-текст календаря — транспорт обмена этапа 1 (PUT и ответы REPORT). */
fun Calendar.icsText(): String = ByteArrayOutputStream().let { out ->
    CalendarOutputter(false).output(this, out)
    out.toString("UTF-8")
}

/** Разбор ICS-текста: битый документ — null, а не исключение. */
fun parseIcs(text: String): Calendar? = runCatching {
    CalendarBuilder().build(StringReader(text))
}.getOrNull()

/** VEVENT из календаря: иного компонента этап 1 не ожидает. */
fun Calendar.vevents(): List<VEvent> = components.filterIsInstance<VEvent>()

/**
 * Разбор серверного VEVENT в доменную модель. null — если это вовсе не
 * дневная метка: нет UID/DTSTART или дата не читается.
 */
fun VEvent.toScheduledEvent(): ScheduledEvent? {
    val uid = properties.byName(Property.UID)?.value ?: return null
    val dtStart = properties.byName(Property.DTSTART) as? DtStart ?: return null
    // VALUE=DATE — «yyyyMMdd»; чужое событие с точным временем
    // («...T090000Z» и родственные формы) берём по календарному дню.
    val anchor = runCatching {
        LocalDate.parse(dtStart.value.take(8), icsDate)
    }.getOrNull() ?: return null
    val summary = properties.byName(Property.SUMMARY)?.value ?: ""

    val declaredType = properties.byName(PROP_TYPE)?.value
        ?.let { value -> ScheduledEventType.entries.find { it.name == value } }
    val rule = (properties.byName(Property.RRULE) as? RRule)?.recur
    val frequency = rule?.getFrequency()?.name

    // Чей VEVENT: свой (X-TNOTE-TYPE есть) или чужой — тогда тип узнаем
    // по RRULE и редкие правила растим в движке этапа 1.
    val type = declaredType ?: when (frequency) {
        "DAILY" -> ScheduledEventType.REPEATING
        "YEARLY" -> ScheduledEventType.BIRTHDAY
        else -> ScheduledEventType.REGULAR
    }

    // Границы повторения: COUNT берём как есть, UNTIL пересчитываем в
    // COUNT (последнее вхождение не позже UNTIL: floor(дни/интервал)+1).
    val interval = if (type == ScheduledEventType.REPEATING) {
        rule?.getInterval()?.takeIf { it > 0 }
    } else {
        null
    }
    val windowCount: Int = when {
        interval == null -> 0

        else -> {
            val count = rule?.getCount()?.takeIf { it > 0 } ?: 0
            // UNTIL бывает и в полной форме с точным временем — через строку
            // достаём только дату: UNTIL=20260110 или UNTIL=20260110T000000Z.
            val until = rule?.getUntil()?.toString()?.take(8)
                ?.let { compact -> runCatching { LocalDate.parse(compact, icsDate) }.getOrNull() }
            when {
                count > 0 -> count

                until != null -> {
                    val between = ChronoUnit.DAYS.between(anchor, until)
                    if (between < 0) 0 else (between / interval).toInt() + 1
                }

                else -> 0
            }
        }
    }
    val resolvedType = when {
        type != ScheduledEventType.REPEATING -> type

        // DAILY без INTERVAL повторить нельзя: как минимум якорный день
        // остаётся видимым — храним как обычное событие.
        interval == null -> ScheduledEventType.REGULAR

        else -> type
    }

    val icon = properties.byName(PROP_ICON)?.value
        ?.let { value -> EventIcon.entries.find { it.name == value } }
        ?: EventIcon.NOTE
    val color = properties.byName(PROP_COLOR)?.value?.toLongOrNull()
        ?: ScheduledEvent.DEFAULT_COLOR
    val hidePast = properties.byName(PROP_HIDE_PAST)?.value == "1"

    return ScheduledEvent(
        id = 0, // локальный id назначит Room; сопоставление ведёт UID
        date = anchor.toString(),
        uid = uid,
        title = summary,
        type = resolvedType,
        icon = icon,
        colorArgb = color,
        repeatIntervalDays = interval,
        repeatDays = windowCount,
        hidePastOccurrences = hidePast,
        position = 0, // локальная сортировка; движок берёт её из локальной копии
    )
}

/** Поиск первого свойства по имени: ical4j getProperty бросается, мы не хотим. */
private fun PropertyList<Property>.byName(name: String): Property? = firstOrNull { it.name == name }
