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
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PROP_TYPE = "X-TNOTE-TYPE"
private const val PROP_ICON = "X-TNOTE-ICON"
private const val PROP_COLOR = "X-TNOTE-COLOR"
private const val PROP_HIDE_PAST = "X-TNOTE-HIDEPAST"

/**
 * 🎂 в начале имени — форма «дня рождения» Яндекса (живая проба: VEVENT
 * с RRULE FREQ=YEARLY и селекторами BYMONTH/BYMONTHDAY по якорю).
 * На границе ICS/events она обратимо ставится и снимается, внутри БД
 * имена дней рождения хранятся чистыми.
 */
private const val BIRTHDAY_ICON = "🎂"

/** PRODID обёртки VCALENDAR, которую отдаёт приложение при PUT. */
private const val PROD_ID = "-//T-Note//iCalendar 1.0//RU"

private val icsDate = DateTimeFormatter.BASIC_ISO_DATE

/** Защитный предел разворота COUNT (дней вперёд) — примерно 270 лет. */
private const val COUNT_EXPANSION_LIMIT_DAYS = 100_000

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
 *    X-TNOTE-*: полноценные CalDAV-серверы хранят их дословно, другие
 *    календарные клиенты игнорируют. Яндекс при PUT выбрасывает их вместе
 *    со всеми неизвестными X-свойствами (проба 2026-11), поэтому копия без
 *    X-TNOTE-* не стирает локальные значения этих полей при переписи
 *    (hasTnoteMetadata → CaldavRemoteEvent.metadataFromIcs → applyRemote);
 *  - повторение: RRULE FREQ=DAILY/WEEKLY/MONTHLY/YEARLY;INTERVAL=N — прямое
 *    отображение на наш якорь [ScheduledEvent.date], частоту и интервал; BYDAY
 *    у WEEKLY — это выбранные дни недели (интерфейс повторения как в Яндексе);
 *    дата-ограничитель — UNTIL (COUNT при разборе разворачивается в дату
 *    последнего вхождения);
 *  - EXDATE — отменённые вхождения повтора («5 марта отменено»): вычёркивают
 *    даты из набора, включая якорную;
 *  - многодневное чужое событие (DTEND на день позже якоря и больше) —
 *    адаптируется в готовую механику «каждый день до последнего дня»
 *    (частота DAYS, интервал 1, ограничитель DTEND − 1): визуально в сетке
 *    это тот же непрерывный ряд дней, отдельное поле endDate не нужно;
 *  - метки времени для конфликтов (этап 2, «последняя правка побеждает»):
 *    CREATED → [ScheduledEvent.createdAt], LAST-MODIFIED →
 *    [ScheduledEvent.modifiedAt]; без них — DTSTAMP (он обязателен по RFC);
 *  - день рождения: RRULE FREQ=YEARLY от полного якоря с годом; февральские 29
 *    и високосный год сводит модель (effectiveBirthdayDate), ICS не знает о них.
 *    Наружу имя дня рождения уходит с 🎂-префиксом (форма Яндекса:
 *    «день рождения» в их клиентах узнаваем по иконке в начале имени), внутрь
 *    храним имя чистым — конвертированное из Яндекса или вернувшееся с
 *    сервера своё, иконку в БД не таскаем;
 *  - «выходной» (WEEKEND) — генерируемая отметка: наружу не экспортируется,
 *    на входе трактуется как REGULAR.
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
    // «Выходной» — локальная генерируемая отметка: наружу уходит как
    // обычное событие, чтобы приёмники с USER-WEEKEND не мучились.
    val exportType = if (type == ScheduledEventType.WEEKEND) {
        ScheduledEventType.REGULAR
    } else {
        type
    }
    return VEvent().apply {
        properties.add(Uid(ownUid))
        // DTSTAMP не добавляем руками: конструктор VEvent сам вставляет
        // момент создания, а его дубль делает тело невалидным по RFC 5545 —
        // Яндекс на такой PUT отвечает 504.
        properties.add(DtStart(Date(anchor.format(icsDate), "yyyyMMdd")))
        properties.add(DtEnd(Date(anchor.plusDays(1).format(icsDate), "yyyyMMdd")))
        properties.add(Summary(birthdaySummaryForIcs()))
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
    }
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

/** Имя дня рождения в ICS: с 🎂-префиксом, если префикса ещё нет. */
private fun ScheduledEvent.birthdaySummaryForIcs(): String {
    if (type != ScheduledEventType.BIRTHDAY) return title
    return if (title.startsWith(BIRTHDAY_ICON)) title else "$BIRTHDAY_ICON $title"
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
 * Защита от ложной починки: живой текст на кириллице или с эмодзи при
 * cp1252-сборке дал бы '?' на каждой не выразимой букве — рост их числа
 * значит «это не кракозябры, не трогать». Latin-1-тексты («Café») после
 * сборки не образуют валидной UTF-8-последовательности и возвращаются
 * как есть; ложная починка возможна только для текста, случайно похожего
 * на задвоенное UTF-8 («Ã©» → «é») — на живых названиях практически
 * не встречается.
 */
internal fun repairMojibake(text: String): String {
    if (text.none { it.code >= 0x80 }) return text
    val bytes = text.toByteArray(Charset.forName("windows-1252"))
    val questionMarks = bytes.count { it == '?'.code.toByte() }
    if (questionMarks > text.count { it == '?' }) return text
    return runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
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
 * Есть ли в VEVENT наши свойства X-TNOTE-*. Экспорт всегда пишет
 * X-TNOTE-TYPE и X-TNOTE-ICON, так что полноценный CalDAV-сервер отвечает
 * «да» всегда; «нет» означает, что сервер наши свойства выбросил (Яндекс
 * режет все неизвестные X-*, проба 2026-11) — движок по этому флагу
 * сохраняет при переписи локальные иконку/цвет/«скрыть прошлое».
 */
internal fun VEvent.hasTnoteMetadata(): Boolean = properties.any {
    it.name == PROP_TYPE || it.name == PROP_ICON || it.name == PROP_COLOR || it.name == PROP_HIDE_PAST
}

/**
 * Разбор серверного VEVENT в доменную модель. null — если это вовсе не
 * дневная метка: нет UID/DTSTART или дата не читается.
 */
fun VEvent.toScheduledEvent(): ScheduledEvent? {
    val uid = properties.byName(Property.UID)?.value ?: return null
    val dtStart = properties.byName(Property.DTSTART) as? DtStart ?: return null
    // VALUE=DATE — «yyyyMMdd»; чужое событие с точным временем
    // («...T090000Z» и родственные формы) берём по календарному дню.
    val anchor = icsDay(dtStart.value) ?: return null
    // Имя могло доехать в «cp1252-прочтении» UTF-8 (Яндекс без BOM —
    // см. repairMojibake): восстанавливаем до разбора типа и правил.
    val summary = repairMojibake(properties.byName(Property.SUMMARY)?.value ?: "")

    // DTEND — эксклюзивная дата конца (RFC 5545): минус день даёт последний
    // показанный день. Однодневное событие имеет DTEND = якорь + 1.
    val spanEnd = (properties.byName(Property.DTEND) as? DtEnd)?.value?.let(::icsDay)?.minusDays(1)

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
    )

    // Границы повторения: UNTIL — как есть (только дата), COUNT — датой
    // последнего вхождения (развёртка по occursOn с защитным пределом).
    val resolved = if (effectiveUnit != null) {
        draft.copy(repeatUntil = draft.windowEnd(rule))
    } else {
        draft
    }

    // Многодневное чужое событие без RRULE (DTEND на день позже якоря и
    // больше): визуально это непрерывный ряд дней — ровно то, что даёт
    // частота DAYS с интервалом 1 и ограничителем на последний день.
    // Отдельное поле endDate не нужно; при живом RRULE правило важнее.
    val multiDayEnd = spanEnd?.takeIf { rule == null && it.isAfter(anchor) }

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

        type == ScheduledEventType.REGULAR && multiDayEnd != null -> resolved.copy(
            type = ScheduledEventType.REPEATING,
            repeatUnit = RepeatUnit.DAYS,
            repeatInterval = 1,
            repeatUntil = multiDayEnd.toString(),
        )

        else -> resolved
    }

    val icon = properties.byName(PROP_ICON)?.value
        ?.let { value -> EventIcon.entries.find { it.name == value } }
        ?: EventIcon.NOTE
    val color = properties.byName(PROP_COLOR)?.value?.toLongOrNull()
        ?: ScheduledEvent.DEFAULT_COLOR
    val hidePast = properties.byName(PROP_HIDE_PAST)?.value == "1"

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
private fun PropertyList<Property>.byName(name: String): Property? = firstOrNull { it.name == name }
