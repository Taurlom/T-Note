package ru.taurlom.tnote.data.sync

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.WeekDay
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Version
import net.fortuna.ical4j.model.property.XProperty
import ru.taurlom.tnote.domain.model.EventIcon
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PROP_TYPE = "X-TNOTE-TYPE"
private const val PROP_ICON = "X-TNOTE-ICON"
private const val PROP_COLOR = "X-TNOTE-COLOR"
private const val PROP_HIDE_PAST = "X-TNOTE-HIDEPAST"

/** Префикс строки-маркера метаданных в DESCRIPTION ([tnoteDescriptionMarker]). */
private const val MARKER_PREFIX = "[T-Note]"

/** Токен маркера «ключ=значение» без пробелов внутри значения. */
private val markerToken = Regex("(\\w+)=(\\S+)")

/**
 * 🎂 в начале имени — форма «дня рождения» Яндекса (живая проба: VEVENT
 * с RRULE FREQ=YEARLY и селекторами BYMONTH/BYMONTHDAY по якорю).
 * На границе ICS/events она обратимо ставится и снимается, внутри БД
 * имена дней рождения хранятся чистыми.
 */
private const val BIRTHDAY_ICON = "🎂"

/**
 * ☀️ в начале имени — перенос нашей метки «Выходной» (WEEKEND): событие
 * на весь день без повторения, наружу — с иконкой (форма та же, что у
 * «дня рождения»: приёмная сторона узнаёт тип по символу в начале имени).
 * На границе ICS/events ставится и снимается обратимо, в БД имена
 * выходных хранятся чистыми.
 */
private const val WEEKEND_ICON = "☀"

/** PRODID обёртки VCALENDAR, которую отдаёт приложение при PUT. */
private const val PROD_ID = "-//T-Note//iCalendar 1.0//RU"

/** Минут в сутках — границы времени события. */
private const val MINUTES_PER_DAY = 24 * 60

/**
 * Длительность события по умолчанию, когда задан только час начала: конец
 * на час позже. Событие нулевой длительности другие календари не показывают.
 */
private const val DEFAULT_DURATION_MINUTES = 60

/** Время события в ICS: начало, конец и сдвиг дня конца (через полночь). */
private data class TimedSpan(
    val startMinutes: Int,
    val endMinutes: Int,
    val endDayOffset: Long,
)

private val icsDate = DateTimeFormatter.BASIC_ISO_DATE

/** Формат чисел в датах — locale не влияет на запись ICS. */
private val LOCALE = Locale.US

/** Защитный предел разворота COUNT (дней вперёд) — примерно 270 лет. */
private const val COUNT_EXPANSION_LIMIT_DAYS = 100_000

/**
 * Мост «событие T-Note ↔ iCalendar (VEVENT)» — фундамент CalDAV-синхронизации:
 * свои события экспортируются как VEVENT (CalDAV PUT), серверные разбираются
 * в доменную модель (REPORT/multiget). Событие без времени — дневная метка,
 * событие со временем — событие с часами; обмен договорён так:
 *
 *  - DTSTART без времени — VALUE=DATE (дневная метка), DTEND — следующий день
 *    (эксклюзивный по RFC 5545);
 *  - DTSTART со временем — DATE-TIME локального времени пользователя без
 *    зоны («плавающее» время, DTSTART:20260305T100000), DTEND — конец
 *    события тем же способом (событие через полночь заканчивается на
 *    следующий день). Зона не пишется: VTIMEZONE мы не объявляем, а
 *    смещение зоны устройства — его настройка, не свойство события;
 *  - чужое событие с точным временем импортируется с часами: момент
 *    приводится к зоне устройства, иначе встреча в другой стране показывалась
 *    бы по чужим часам; без времени — по календарному дню, как было.
 *  - [ScheduledEvent.position] — локальная сортировка, в ICS не экспортируется:
 *    очередность восстанавливается из локальной копии, сопоставленной по UID,
 *    а не с сервера;
 *  - цвет, иконка, тип и «прошлые вхождения скрыты» — нестандартные свойства
 *    X-TNOTE-*: полноценные CalDAV-серверы хранят их дословно, другие
 *    календарные клиенты игнорируют. Яндекс при PUT выбрасывает их вместе
 *    со всеми неизвестными X-свойствами (проба 2026-11), поэтому копия без
 *    X-TNOTE-* не стирает локальные значения этих полей при переписи
 *    (hasTnoteMetadata → CaldavRemoteEvent.metadataFromIcs → applyRemote);
 *  - иконка, цвет и «прошлые вхождения скрыты» едут и через серверы без
 *    X-свойств: экспорт пишет в DESCRIPTION маркер-строку
 *    «[T-Note] icon=… color=… hide=1» ([tnoteDescriptionMarker]) — стандартное
 *    свойство Яндекс хранит дословно, как и текст заметок дня (NoteIcsMapper).
 *    При разборе источники по старшинству: X-TNOTE-* → DESCRIPTION-маркер →
 *    локальные значения (чужое событие без метаданных);
 *  - повторение: RRULE FREQ=DAILY/WEEKLY/MONTHLY/YEARLY;INTERVAL=N — прямое
 *    отображение на наш якорь [ScheduledEvent.date], частоту и интервал; BYDAY
 *    у WEEKLY — это выбранные дни недели (интерфейс повторения как в Яндексе);
 *    дата-ограничитель — UNTIL (COUNT при разборе разворачивается в дату
 *    последнего вхождения);
 *  - EXDATE — отменённые вхождения повтора («5 марта отменено»): вычёркивают
 *    даты из набора, включая якорную;
 *  - многодневное чужое событие без времени (DTEND на день позже якоря и
 *    больше) — адаптируется в готовую механику «каждый день до последнего
 *    дня» (частота DAYS, интервал 1, ограничитель последним днём): визуально
 *    в сетке это тот же непрерывный ряд дней, отдельное поле endDate не
 *    нужно; часы события при этом сохраняются — они одинаковы для всех дней
 *    ряда;
 *  - многодневное чужое событие с часами («23:30 → 00:30», «1 марта 10:00 →
 *    5 марта 18:00») — остаётся обычным событием с датой конца в endDate
 *    (часы конца — от полуночи дня endDate). Повтором оно не становится:
 *    один UID — один объект, без дублей при обратной отправке. В календаре
 *    такое событие показывается по дням с обрезанными часами («23:30 – 24:00»
 *    и «00:00 – 00:30»);
 *  - метки времени для конфликтов (этап 2, «последняя правка побеждает»):
 *    CREATED → [ScheduledEvent.createdAt], LAST-MODIFIED →
 *    [ScheduledEvent.modifiedAt]; без них — DTSTAMP (он обязателен по RFC);
 *  - день рождения: RRULE FREQ=YEARLY от полного якоря с годом; февральские 29
 *    и високосный год сводит модель (effectiveBirthdayDate), ICS не знает о них.
 *    Наружу имя дня рождения уходит с 🎂-префиксом (форма Яндекса:
 *    «день рождения» в их клиентах узнаваем по иконке в начале имени), внутрь
 *    храним имя чистым — конвертированное из Яндекса или вернувшееся с
 *    сервера своё, иконку в БД не таскаем;
 *  - метка «Выходной» (WEEKEND) — событие на весь день без повторения;
 *    наружу уходит с ☀️-префиксом имени (форма переноса, как 🎂 у дня
 *    рождения), на входе «☀️ Имя» без правила конвертируется в WEEKEND
 *    с чистым именем.
 *
 * Годовые селекторы-дубликаты якоря «BYMONTH=<месяц якоря>;
 * BYMONTHDAY=<день якоря>» (форма годовых событий веб-Яндекса, проверено
 * живой пробой) по набору вхождений равны голому YEARLY — считаем их
 * представимыми. Остальные непредставимые чужие RRULE (BYDAY с позициями
 * «вторый вторник месяца», не совпадающие с якорем селекторы, и прочие)
 * разбираются как REGULAR на якорный день — без попытки угадать.
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
    // «Выходной» теперь полноценно ездит наружу: тип в X-TNOTE-TYPE пишется
    // как есть — узнавание у приёмников без X-свойств (Яндекс) идёт по
    // ☀️-префиксу имени (summaryForIcs).
    val exportType = type
    return VEvent().apply {
        properties.add(Uid(ownUid))
        // DTSTAMP не добавляем руками: конструктор VEvent сам вставляет
        // момент создания, а его дубль делает тело невалидным по RFC 5545 —
        // Яндекс на такой PUT отвечает 504.
        properties.add(DtStart(icsStart(anchor)))
        properties.add(icsEnd(anchor))
        properties.add(Summary(summaryForIcs()))
        // RRULE пишут повторения: «выходной» наружу вообще не ходит,
        // и повторение без частоты повторить нечего — идёт без RRULE.
        if (type == ScheduledEventType.REPEATING) {
            repeatUnit?.let { unit ->
                properties.add(RRule(recurValue(unit, anchor)))
            }
        }
        if (type == ScheduledEventType.BIRTHDAY) {
            properties.add(RRule("FREQ=YEARLY"))
        }
        // EXDATE — отменённые вхождения повтора. Своим событиям отмены
        // пока не прикладываются, набор копится только от сервера.
        // Список обязан знать тип DATE, иначе даты сериализуются формой
        // с временем суток (T030000) и другие клиенты их не поймут.
        if (exdates.isNotEmpty()) {
            val exDateProp = ExDate(
                DateList(Value.DATE).apply {
                    exdates.sorted().forEach { day -> add(Date(day.format(icsDate), "yyyyMMdd")) }
                },
            )
            exDateProp.parameters.add(Value.DATE)
            properties.add(exDateProp)
        }
        // Метки времени для разрешения конфликтов: DTSTAMP («когда объект
        // перезаписан») ical4j ставит сам, CREATED и LAST-MODIFIED — наши
        // пользовательские моменты создания и правки.
        createdAt?.let { properties.add(Created(DateTime(it))) }
        modifiedAt?.let { properties.add(LastModified(DateTime(it))) }
        properties.add(XProperty(PROP_TYPE, exportType.name))
        properties.add(XProperty(PROP_ICON, icon.name))
        if (colorArgb != ScheduledEvent.DEFAULT_COLOR) {
            properties.add(XProperty(PROP_COLOR, colorArgb.toString()))
        }
        if (hidePastOccurrences) {
            properties.add(XProperty(PROP_HIDE_PAST, "1"))
        }
        // DESCRIPTION-маркер — тот же набор метаданных запасным каналом,
        // для серверов, которые X-* выбрасывают (Яндекс). Пишется всегда,
        // включая дефолты: сброс иконки/цвета доезжает до других устройств
        // только явным «icon=NOTE» — отсутствие маркера при разборе значит
        // «метаданных нет» и включает хранение локальных значений.
        properties.add(Description(tnoteDescriptionMarker(icon, colorArgb, hidePastOccurrences)))
    }
}

/**
 * DTSTART события: с временем — DATE-TIME локального времени пользователя,
 * без времени — дневная метка (VALUE=DATE).
 */
private fun ScheduledEvent.icsStart(anchor: LocalDate): Date {
    val span = timedSpan() ?: return Date(anchor.format(icsDate), "yyyyMMdd")
    return icsDateTime(anchor, span.startMinutes)
}

/**
 * DTEND события. Дневная метка — следующий день (эксклюзивный конец по
 * RFC 5545). Событие со временем — конец в тот же день или на следующий
 * (событие через полночь). Событие длиннее суток (у него есть endDate) —
 * конец на дату endDate, своими часами: так объект остаётся один и
 * описывает всю длительность.
 */
private fun ScheduledEvent.icsEnd(anchor: LocalDate): DtEnd {
    val span = timedSpan()
        ?: return DtEnd(Date(anchor.plusDays(1).format(icsDate), "yyyyMMdd"))
    val endDay = endDateOrNull()
    val endTime = endMinutes
    if (endDay != null && endTime != null) {
        return DtEnd(icsDateTime(endDay, endTime))
    }
    return DtEnd(icsDateTime(anchor.plusDays(span.endDayOffset), span.endMinutes))
}

/**
 * Время события в минутах от полуночи якорного дня и сдвиг дня конца.
 * null — у события нет времени, это дневная метка.
 *
 * Правила краёв: задан только конец — начало считается полночью; задан
 * только начало — конец равен началу плюс час (событие нулевой длительности
 * другие календари не показывают вовсе); конец раньше начала — событие идёт
 * через полночь, конец приходит на следующий день.
 */
private fun ScheduledEvent.timedSpan(): TimedSpan? {
    val start = startMinutes
    val end = endMinutes
    if (start == null && end == null) return null
    val from = start ?: 0
    val rawEnd = end ?: (from + DEFAULT_DURATION_MINUTES)
    val overnight = end != null && end < from
    val overflow = if (rawEnd >= MINUTES_PER_DAY) 1L else 0L
    return TimedSpan(
        startMinutes = from,
        endMinutes = rawEnd % MINUTES_PER_DAY,
        endDayOffset = if (overnight) 1L else overflow,
    )
}

/**
 * DATE-TIME без зоны — «плавающее» время по RFC 5545: DTSTART:20260305T100000.
 * Зону не объявляем: VTIMEZONE в теле CalDAV мы не описываем, а смещение
 * зоны устройства — настройка телефона, а не свойство события. Разбор такой
 * строки обратно даёт то же местное время (см. [localDateTimeOf]).
 */
internal fun icsDateTime(day: LocalDate, minutes: Int): DateTime = DateTime(
    String.format(
        LOCALE,
        "%04d%02d%02dT%02d%02d00",
        day.year,
        day.monthValue,
        day.dayOfMonth,
        minutes / 60,
        minutes % 60,
    ),
    "yyyyMMdd'T'HHmmss",
    false,
)

/**
 * Момент времени свойства в зоне устройства; null — у свойства нет времени
 * (дневная метка). «Плавающее» время ical4j разбирает в зоне устройства,
 * UTC — с переводом в неё, чужой TZID — тоже: чужое событие показывается
 * по местным часам пользователя.
 */
private fun localDateTimeOf(value: Date?): LocalDateTime? {
    val dateTime = value as? DateTime ?: return null
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTime.time), ZoneId.systemDefault())
}

/** RRULE-значение нашего повторения: FREQ, INTERVAL, BYDAY (недели), UNTIL. */
private fun ScheduledEvent.recurValue(unit: RepeatUnit, anchor: LocalDate): String {
    val interval = repeatInterval.takeIf { it > 0 } ?: 1
    val parts = mutableListOf("FREQ=${unit.icsFreq}", "INTERVAL=$interval")
    if (unit == RepeatUnit.WEEKS) {
        ScheduledEvent.weekdaysToCode(repeatWeekdays)?.let { parts += "BYDAY=$it" }
    }
    // UNTIL для VALUE=DATE — дата без времени (совпадает с типом DTSTART).
    repeatUntil?.let { parts += "UNTIL=${it.replace("-", "")}" }
    return parts.joinToString(";")
}

/** Имя события в ICS: типовые иконки-префиксы — форма Яндекса для «дня
 *  рождения» и договорённый перенос для «Выходного»; если префикс уже
 *  стоит, он не задваивается. */
private fun ScheduledEvent.summaryForIcs(): String {
    val icon = when (type) {
        ScheduledEventType.BIRTHDAY -> BIRTHDAY_ICON
        ScheduledEventType.WEEKEND -> "$WEEKEND_ICON\uFE0F"
        else -> return title
    }
    return if (title.startsWith(icon)) title else "$icon $title"
}

/**
 * «☀️ Имя» → «Имя» — форма нашей метки «Выходной» на границе ICS; null —
 * имя таковым не является (без иконки в начале). Селектор варианта после
 * иконки и пробелы-разделители съедаются.
 */
private fun weekendTitleWithoutIcon(summary: String): String? {
    if (!summary.startsWith(WEEKEND_ICON)) return null
    return summary
        .removePrefix(WEEKEND_ICON)
        .removePrefix("\uFE0F")
        .trimStart()
}

/**
 * «🎂 Имя» → «Имя» — форма дня рождения Яндекса; null — имя таковым
 * не является (без иконки в начале). Селектор варианта после иконки
 * и пробелы-разделители съедаются.
 */
private fun birthdayTitleWithoutIcon(summary: String): String? {
    if (!summary.startsWith(BIRTHDAY_ICON)) return null
    return summary
        .removePrefix(BIRTHDAY_ICON)
        .removePrefix("\uFE0F")
        .trimStart()
}

/** Наша единица частоты → FREQ из RFC 5545. */
private val RepeatUnit.icsFreq: String
    get() = when (this) {
        RepeatUnit.DAYS -> "DAILY"
        RepeatUnit.WEEKS -> "WEEKLY"
        RepeatUnit.MONTHS -> "MONTHLY"
        RepeatUnit.YEARS -> "YEARLY"
    }

/**
 * Починка «UTF-8, прочитанного как cp1252». Парсер text/calendar Яндекса
 * угадывает кодировку PUT-тела: UTF-8 BOM или байты из «дыр» cp1252
 * (0x81/0x8D/0x8F/0x90/0x9D — в кириллице это «Ё», «э», «я», «А», «Н»,
 * «с») включают UTF-8, прочие тела декодируются как cp1252 и хранятся
 * кракозябрами (charset=... игнорируется; проба и бисекция 2026-11).
 * Свой PUT шлётся с BOM (CaldavHttpClient), но тела чужих клиентов без
 * BOM и старые наши PUT-ы лежат в хранилище уже испорченными. Такой
 * шаблон обратим: символ строки — это байт корректного UTF-8, их надо
 * собрать обратно и декодировать.
 *
 * Защита от ложной починки: живой текст на кириллице или с эмодзи не
 * выразим в cp1252 — строгий энкодер (REPORT) отказывается его кодировать,
 * и текст возвращается как есть. Реальные кракозябры собраны из байтов
 * cp1252 читателем сервера и потому всегда кодируются обратно. Строгий
 * режим обязателен обеим сторонам взаимосвязи, но особенно энкодеру:
 * замена невыразимых символов нестандартизована — JVM подставляет '?'
 * (0x3F), Android (ICU) — SUB (0x1A), а байты 0x1A — валидный UTF-8, и
 * строгий UTF-8-декодер молча возвращает испорченный текст (регрессия
 * 2026-11-10: все импортированные из Яндекса названия с кириллицей
 * ложились в базу U+001A и роняли тип BIRTHDAY). Latin-1-тексты («Café»)
 * после сборки не образуют валидной UTF-8-последовательности и
 * возвращаются как есть; ложная починка возможна только для текста,
 * случайно похожего на задвоенное UTF-8 («Ã©» → «é») — на живых
 * названиях практически не встречается.
 */
internal fun repairMojibake(text: String): String {
    if (text.none { it.code >= 0x80 }) return text
    val bytes = runCatching {
        Charset.forName("windows-1252").newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
    }.getOrNull() ?: return text
    return runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(bytes)
            .toString()
    }.getOrDefault(text)
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
 * Строка-маркер метаданных для DESCRIPTION: «[T-Note] icon=TRAVEL color=42
 * hide=1». Канал для серверов, режущих X-TNOTE-* (Яндекс — проба 2026-11):
 * DESCRIPTION — стандартное свойство, Яндекс хранит его дословно (текст
 * заметок дня уже ездит им, NoteIcsMapper). Маркер составлен из одних
 * ASCII-символов и потому неуязвим для cp1252-прочтения тел без BOM —
 * ASCII в cp1252 и UTF-8 это одни и те же байты, repairMojibake его не
 * трогает.
 *
 * Пишется всегда, включая дефолты: отсутствие маркера при разборе значит
 * «метаданных нет» (чужое событие), поэтому сброс иконки/цвета доезжает
 * до других устройств только явным «icon=NOTE». Цвет — Android-шестнад-
 * цатеричный ARGB («#FFE53935»); ключи color/hide опускаются при значении
 * по умолчанию — та же условность, что у экспорта X-свойств.
 */
internal fun tnoteDescriptionMarker(icon: EventIcon, colorArgb: Long, hidePast: Boolean): String = buildString {
    append(MARKER_PREFIX)
    append(" icon=").append(icon.name)
    if (colorArgb != ScheduledEvent.DEFAULT_COLOR) {
        append(" color=")
        if (colorArgb > 0) {
            // Читаемая форма для веб-Яндекса: Android-шестнадцатеричный
            // ARGB («#FFE53935» = непрозрачный красный «E53935»).
            append('#').append(colorArgb.toString(16).padStart(8, '0').uppercase())
        } else {
            // Отрицательный ARGB старых данных не выразим hex-формой —
            // десятичной (разбор понимает обе).
            append(colorArgb)
        }
    }
    if (hidePast) append(" hide=1")
}

/**
 * Значение color-ключа маркера: «#FFE53935»/«#E53935» (шестнадцатеричный
 * ARGB, у шестизначной RGB-формы альфа непрозрачности дописывается) или
 * десятичный ARGB — форма маркеров самых первых версий. Неразборчивое — null.
 */
private fun markerColor(value: String): Long? {
    val hex = value.removePrefix("#")
    return if (hex != value) {
        when (hex.length) {
            6 -> hex.toLongOrNull(16)?.or(0xFF000000L)
            8 -> hex.toLongOrNull(16)
            else -> null
        }
    } else {
        value.toLongOrNull()
    }
}

/** Распознанные поля DESCRIPTION-маркера; null — ключа нет в строке. */
internal data class TnoteMarker(val icon: EventIcon?, val colorArgb: Long?, val hidePast: Boolean?)

/**
 * Разбор DESCRIPTION-маркера. Маркер ищется в любом месте текста —
 * пользователь мог дописать своё вокруг нашей строки в веб-Яндексе;
 * ключи читаются до первого токена без формы «ключ=значение».
 * Неизвестные ключи будущих версий пропускаются, неразборчивые значения
 * дают null по своему полю. Ни одного распознанного ключа (в том числе
 * у голого префикса) — null: «маркера нет».
 */
internal fun tnoteMarkerFrom(description: String): TnoteMarker? {
    val start = description.indexOf(MARKER_PREFIX).takeIf { it >= 0 } ?: return null
    var icon: EventIcon? = null
    var color: Long? = null
    var hide: Boolean? = null
    for (token in description.substring(start + MARKER_PREFIX.length).trimStart().split(Regex("\\s+"))) {
        val match = markerToken.matchEntire(token) ?: break
        val (key, value) = match.destructured
        when (key) {
            "icon" -> icon = EventIcon.entries.find { it.name == value }

            "color" -> color = markerColor(value)

            "hide" -> hide = value == "1"

            // Ключи будущих версий не мешают разбору известных полей.
            else -> Unit
        }
    }
    return if (icon == null && color == null && hide == null) null else TnoteMarker(icon, color, hide)
}

/**
 * Есть ли у VEVENT непустое DESCRIPTION. Для решения о бэкфилле маркера:
 * у старых событий нашего экспорта description нет вовсе — их перезапись
 * ничего пользовательского не сотрёт; текст у события мог набрать только
 * человек в другом клиенте (наш маркер-строка — не текст, но при его
 * наличии у события уже есть метаданные и бэкфилл не обсуждается).
 */
internal fun VEvent.hasDescriptionText(): Boolean = properties.byName(Property.DESCRIPTION)?.value?.isNotBlank() == true

/**
 * Есть ли в VEVENT наши метаданные — X-TNOTE-* или DESCRIPTION-маркер.
 * Экспорт всегда пишет X-TNOTE-TYPE и X-TNOTE-ICON, так что полноценный
 * CalDAV-сервер отвечает «да» всегда; «нет» означает, что сервер наши
 * свойства выбросил (Яндекс режет все неизвестные X-*, проба 2026-11) и
 * маркера в DESCRIPTION не осталось — движок по этому флагу сохраняет
 * при переписи локальные иконку/цвет/«скрыть прошлое».
 */
internal fun VEvent.hasTnoteMetadata(): Boolean = properties.any {
    it.name == PROP_TYPE || it.name == PROP_ICON || it.name == PROP_COLOR || it.name == PROP_HIDE_PAST
} || tnoteMarkerFrom(properties.byName(Property.DESCRIPTION)?.value ?: "") != null

/**
 * Разбор серверного VEVENT в доменную модель. null — если это вовсе не
 * дневная метка: нет UID/DTSTART или дата не читается.
 */
fun VEvent.toScheduledEvent(): ScheduledEvent? {
    val uid = properties.byName(Property.UID)?.value ?: return null
    val dtStart = properties.byName(Property.DTSTART) as? DtStart ?: return null
    // DTSTART с временем (DATE-TIME) приводим к местному времени устройства:
    // чужое событие показывается по часам пользователя, а его дата — по
    // локальной дате того же момента (UTC-полночь в другой зоне — уже
    // следующий день). Дневная метка (VALUE=DATE) читается как была.
    val startMoment = localDateTimeOf(dtStart.getDate())
    val anchor = startMoment?.toLocalDate() ?: icsDay(dtStart.value) ?: return null
    val startMinutes = startMoment?.let { it.hour * 60 + it.minute }
    // DTEND с временем — конец события в минутах от полуночи; без времени
    // (дневная метка) конца по времени у события нет.
    val endMoment = (properties.byName(Property.DTEND) as? DtEnd)?.let { localDateTimeOf(it.getDate()) }
    val endMinutes = endMoment?.let { it.hour * 60 + it.minute }
    // Имя могло доехать в «cp1252-прочтении» UTF-8 (Яндекс без BOM —
    // см. repairMojibake): восстанавливаем до разбора типа и правил.
    val summary = repairMojibake(properties.byName(Property.SUMMARY)?.value ?: "")

    // DTEND: у дневной метки — эксклюзивная дата конца (RFC 5545), минус
    // день даёт последний показанный день; у события со временем — точный
    // момент, и последним показанным днём становится его дата.
    val dtEnd = properties.byName(Property.DTEND) as? DtEnd
    // Событие с часами длиннее суток: конец приходит на другой день
    // («23:30 → 00:30», «1 марта 10:00 → 5 марта 18:00»). Такое событие
    // остаётся одним объектом с одним UID, а его конец хранится в endDate:
    // в календаре событие разворачивается по дням с обрезанными часами
    // (см. ScheduledEvent.windowOn).
    val timedEndDate = endMoment?.toLocalDate()?.takeIf { it.isAfter(anchor) }
    val spanEnd = if (endMoment != null) {
        endMoment.toLocalDate()
    } else {
        dtEnd?.value?.let(::icsDay)?.minusDays(1)
    }

    // Отменённые вхождения повтора (EXDATE): могут идти несколькими строками
    // и с перечислением через запятую; VALUE=DATE и точное время — обе формы.
    val exdates = properties
        .filter { it.name == Property.EXDATE }
        .flatMap { property -> (property as? ExDate)?.dates ?: emptyList() }
        .mapNotNull { exception -> icsDay(exception.toString()) }
        .toSet()

    // Метки времени для разрешения конфликтов (этап 2, «последняя правка
    // побеждает»): CREATED/LAST-MODIFIED пользовательские, DTSTAMP обязателен
    // по RFC и потому есть всегда — им и закрываем пропуски.
    val stamp = (properties.byName(Property.DTSTAMP) as? DtStamp)?.date?.time
    val createdAt = (properties.byName(Property.CREATED) as? Created)?.date?.time ?: stamp
    val modifiedAt = (properties.byName(Property.LAST_MODIFIED) as? LastModified)?.date?.time ?: stamp

    val rule = (properties.byName(Property.RRULE) as? RRule)?.recur

    val declaredType = properties.byName(PROP_TYPE)?.value
        ?.let { value -> ScheduledEventType.entries.find { it.name == value } }
    val frequency = rule?.getFrequency()?.name

    // Частота из FREQ; INTERVAL в RFC не обязателен — умолчание 1.
    val unit = when (frequency) {
        "DAILY" -> RepeatUnit.DAYS
        "WEEKLY" -> RepeatUnit.WEEKS
        "MONTHLY" -> RepeatUnit.MONTHS
        "YEARLY" -> RepeatUnit.YEARS
        else -> null
    }
    val interval = rule?.getInterval()?.takeIf { it > 0 } ?: 1
    // BYDAY выражает наши дни недели только у недельной частоты и без
    // позиционных префиксов («вторый вторник» нашей моделью не выразить).
    val weekdays = if (unit == RepeatUnit.WEEKS) {
        rule?.dayList
            ?.filter { it.offset == 0 }
            ?.mapNotNull { weekdayOf(it.day) }
            ?.toSet()
            ?: emptySet()
    } else {
        emptySet()
    }

    // Селекторы BY* нашей моделью не выразить — правило целиком
    // отбрасывается: якорный день остаётся, остальное не выдумываем.
    // Исключение — годовые селекторы-дубликаты якоря ниже.
    val representable = rule != null && unit != null &&
        rule.isRepresentableByModel(unit, anchor)

    // Чей VEVENT: свой (X-TNOTE-TYPE есть) или чужой — тогда тип узнаем
    // по RRULE. Годовой повтор чужого события — не день рождения (подарок
    // и сдвиг 29 февраля — про людей), а честное REPEATING/YEARS; исключение
    // — форма Яндекса: годовой VEVENT с 🎂 в начале имени и без ограничителей,
    // это их «день рождения» — конвертируем в наш тип веткой ниже.
    val type = declaredType ?: if (representable) ScheduledEventType.REPEATING else ScheduledEventType.REGULAR
    // Правило имеет смысл только для REPEATING: у BIRTHDAY повторение —
    // часть типа, отдельного правила у нашего события нет.
    val effectiveUnit = if (type == ScheduledEventType.REPEATING && representable) unit else null
    val effectiveWeekdays = if (effectiveUnit == RepeatUnit.WEEKS) weekdays else emptySet()

    val draft = ScheduledEvent(
        id = 0, // локальный id назначит Room; сопоставление ведёт UID
        date = anchor.toString(),
        uid = uid,
        title = summary,
        type = type,
        repeatUnit = effectiveUnit,
        repeatInterval = interval,
        repeatWeekdays = effectiveWeekdays,
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        // Конец на другом дне — событие длиннее суток: дата конца в
        // отдельном поле, тип при этом остаётся REGULAR (повтором такое
        // событие быть не может).
        endDate = timedEndDate?.toString(),
    )

    // Границы повторения: UNTIL — как есть (только дата), COUNT — датой
    // последнего вхождения (развёртка по occursOn с защитным пределом).
    val resolved = if (effectiveUnit != null) {
        draft.copy(repeatUntil = draft.windowEnd(rule))
    } else {
        draft
    }

    // Многодневное чужое событие без времени (DTEND на день позже якоря и
    // больше): визуально это непрерывный ряд дней — ровно то, что даёт
    // частота DAYS с интервалом 1 и ограничителем на последний день.
    // Отдельное поле endDate не нужно; при живом RRULE правило важнее.
    // Событие с часами сюда не попадает: у него есть endDate, и повтором
    // оно не становится (одна строка — один объект на сервере).
    val multiDayEnd = spanEnd?.takeIf { endMoment == null && rule == null && it.isAfter(anchor) }

    // День рождения Яндекса: годовой круг без ограничителей и селекторов-
    // недубликатов (уже проверено representable выше), имя с 🎂 — наш
    // тип BIRTHDAY ровно про это: вечный годовой круг от месяца/дня якоря.
    // Иконку в БД не храним (экспорт вернёт её сам), правило в RRULE-поля
    // не разлагается — у BIRTHDAY оно часть типа.
    val birthdayTitle = birthdayTitleWithoutIcon(summary)
    val isForeignBirthday = birthdayTitle != null &&
        declaredType == null &&
        representable &&
        unit == RepeatUnit.YEARS &&
        interval == 1 &&
        resolved.repeatUntil == null

    // Перенос нашей метки «Выходной»: событие на весь день с ☀️ в начале
    // имени и без правила (выходной не повторяется). Чужой VEVENT с такой
    // формой конвертируется в WEEKEND с чистым именем — так же, как
    // 🎂-годовой круг конвертируется в день рождения.
    val weekendTitle = weekendTitleWithoutIcon(summary)
    val isForeignWeekend = weekendTitle != null &&
        declaredType == null &&
        !representable

    val adapted = when {
        isForeignBirthday -> resolved.copy(
            type = ScheduledEventType.BIRTHDAY,
            title = birthdayTitle.orEmpty(),
            repeatUnit = null,
            repeatInterval = 1,
            repeatWeekdays = emptySet(),
            repeatUntil = null,
        )

        // Свой BIRTHDAY вернулся с сервера: экспорт добавил иконку в
        // SUMMARY — в БД имя тоже держим чистым, иначе редактор покажет
        // двойной 🎂 после каждой синхронизации.
        type == ScheduledEventType.BIRTHDAY && birthdayTitle != null -> resolved.copy(title = birthdayTitle)

        isForeignWeekend -> resolved.copy(
            type = ScheduledEventType.WEEKEND,
            title = weekendTitle.orEmpty(),
            repeatUnit = null,
            repeatInterval = 1,
            repeatWeekdays = emptySet(),
            repeatUntil = null,
        )

        // Свой WEEKEND вернулся с сервера: экспорт добавил иконку в
        // SUMMARY — в БД имя держим чистым, как у дня рождения.
        type == ScheduledEventType.WEEKEND && weekendTitle != null -> resolved.copy(title = weekendTitle)

        type == ScheduledEventType.REGULAR && multiDayEnd != null -> resolved.copy(
            type = ScheduledEventType.REPEATING,
            repeatUnit = RepeatUnit.DAYS,
            repeatInterval = 1,
            repeatUntil = multiDayEnd.toString(),
        )

        else -> resolved
    }

    // Маркер в DESCRIPTION — запасной канал метаданных для серверов без
    // X-свойств (Яндекс). X-TNOTE-* старше: сервер, донёсший их дословно,
    // донёс и маркер — расхождения между каналами нет, а при неполной
    // потере X-свойств именно они точнее.
    val marker = tnoteMarkerFrom(properties.byName(Property.DESCRIPTION)?.value ?: "")

    // Старшинство источников: X-TNOTE-* → маркер DESCRIPTION → дефолт.
    // «Скрыть прошлое» различает и явное «0» у X-свойства (присутствует,
    // но ложь) от отсутствия свойства (тогда решает маркер).
    val icon = properties.byName(PROP_ICON)?.value
        ?.let { value -> EventIcon.entries.find { it.name == value } }
        ?: marker?.icon
        ?: EventIcon.NOTE
    val color = properties.byName(PROP_COLOR)?.value?.toLongOrNull()
        ?: marker?.colorArgb
        ?: ScheduledEvent.DEFAULT_COLOR
    val hidePast = properties.byName(PROP_HIDE_PAST)?.let { it.value == "1" }
        ?: marker?.hidePast
        ?: false

    return adapted.copy(
        icon = icon,
        colorArgb = color,
        hidePastOccurrences = hidePast,
        exdates = exdates,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )
}

/** "20260305" / "20260305T000000Z" → LocalDate; неразборчивое — null. */
private fun icsDay(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(8), icsDate) }.getOrNull()

/** UNTIL/COUNT правила в наш «повторять до»; ни того ни другого — null (бессрочно). */
private fun ScheduledEvent.windowEnd(rule: Recur?): String? {
    // UNTIL бывает в полной форме с точным временем — через строку
    // достаём только дату: UNTIL=20260110 или UNTIL=20260110T000000Z.
    val until = rule?.getUntil()?.toString()?.take(8)
        ?.let { compact -> runCatching { LocalDate.parse(compact, icsDate) }.getOrNull() }
    if (until != null) return until.toString()
    val count = rule?.getCount()?.takeIf { it > 0 } ?: return null
    return nthOccurrence(count)?.toString()
}

/** Дата [count]-го вхождения события (от якоря) или null, если не нашли. */
private fun ScheduledEvent.nthOccurrence(count: Int): LocalDate? {
    var cursor = dateOrNull() ?: return null
    var remaining = count
    var guard = 0
    while (guard++ < COUNT_EXPANSION_LIMIT_DAYS) {
        if (occursOn(cursor) && --remaining == 0) return cursor
        cursor = cursor.plusDays(1)
    }
    return null
}

/**
 * Выразимо ли правило нашей моделью: у DAYS и у WEEKS не должно быть иных
 * селекторов BY*, кроме BYDAY (без позиций у WEEKLY); MONTHLY — только совсем
 * без селекторов; YEARLY — без селекторов либо только с селекторами-
 * дубликатами якоря (см. [yearlySelectorsMatchAnchor]). Время внутри дня
 * (BYHOUR и родня) не выразимо всегда — наши события дневные метки.
 */
private fun Recur.isRepresentableByModel(unit: RepeatUnit, anchor: LocalDate): Boolean {
    if (hourList.isNotEmpty() || minuteList.isNotEmpty() || secondList.isNotEmpty()) return false
    if (setPosList.isNotEmpty() || weekNoList.isNotEmpty()) return false
    // Селекторы месяца/года допустимы только у YEARLY и только в форме-
    // дубликате якоря; у прочих частот любое их присутствие не выразимо.
    if (unit != RepeatUnit.YEARS &&
        (monthList.isNotEmpty() || monthDayList.isNotEmpty() || yearDayList.isNotEmpty())
    ) {
        return false
    }
    return when (unit) {
        RepeatUnit.DAYS -> dayList.isEmpty()
        RepeatUnit.WEEKS -> dayList.all { it.offset == 0 }
        RepeatUnit.MONTHS -> dayList.isEmpty()
        RepeatUnit.YEARS -> dayList.isEmpty() && yearlySelectorsMatchAnchor(anchor)
    }
}

/**
 * Годовые селекторы, при которых правило по набору вхождений равно голому
 * YEARLY от [anchor]: селекторов нет вовсе — либо дублирующая форма Яндекса
 * «BYMONTH=<месяц якоря>;BYMONTHDAY=<день якоря>» (живая проба, ноябрь
 * 2026: все годовые события веб-Яндекса, включая «день рождения», идут
 * ею). Не сходящиеся с якорем значения не представимы: правило отбрасываем.
 */
private fun Recur.yearlySelectorsMatchAnchor(anchor: LocalDate): Boolean {
    if (monthList.isEmpty() && monthDayList.isEmpty() && yearDayList.isEmpty()) return true
    return monthList.size == 1 && monthList.first().monthOfYear == anchor.monthValue &&
        monthDayList.size == 1 && monthDayList.first() == anchor.dayOfMonth &&
        yearDayList.isEmpty()
}

/** Код дня недели ical4j (MO..SU) в java.time. */
private fun weekdayOf(day: WeekDay.Day): DayOfWeek? = when (day) {
    WeekDay.Day.MO -> DayOfWeek.MONDAY
    WeekDay.Day.TU -> DayOfWeek.TUESDAY
    WeekDay.Day.WE -> DayOfWeek.WEDNESDAY
    WeekDay.Day.TH -> DayOfWeek.THURSDAY
    WeekDay.Day.FR -> DayOfWeek.FRIDAY
    WeekDay.Day.SA -> DayOfWeek.SATURDAY
    WeekDay.Day.SU -> DayOfWeek.SUNDAY
}

/** Поиск первого свойства по имени: ical4j getProperty бросается, мы не хотим. */
internal fun PropertyList<Property>.byName(name: String): Property? = firstOrNull { it.name == name }
