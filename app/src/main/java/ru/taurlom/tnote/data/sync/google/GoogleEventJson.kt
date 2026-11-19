package ru.taurlom.tnote.data.sync.google

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import org.json.JSONArray
import org.json.JSONObject
import ru.taurlom.tnote.data.sync.byName
import ru.taurlom.tnote.data.sync.descriptionHasTextBeyondMarker
import ru.taurlom.tnote.data.sync.icsDateTime
import ru.taurlom.tnote.data.sync.noteUidFor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Конвертеры «событие Google Calendar API v3 ↔ VEVENT» — мост между
 * JSON-транспортом Google и общими разборщиками [ru.taurlom.tnote.data.sync]
 * (toScheduledEvent/toIcsEvent). Промежуточной формой выбран ical4j-VEVENT,
 * а не прямое отображение в модель: вся логика RRULE/EXDATE/маркера-описания
 * уже есть и протестирована в одном месте, вторая копия закона не нужна.
 *
 * События Google бывают «весь день» (start.date, конец эксклюзивен — как
 * RFC 5545) и «с точным временем» (start.dateTime, конец — точный момент).
 * Модель T-Note знает только дневные метки, поэтому dateTime читается по
 * локальной для события дате, а конец переводится в эксклюзивную дату
 * следующего дня (+1) — так «−1 день» разбора toScheduledEvent даёт верный
 * последний показываемый день.
 *
 * Идентификаторы: ключ сопоставления — наш UID, он же идентификатор события
 * Google. Правила Google жёсткие (докуменация events.resource): id — только
 * символы base32hex (строчные a–v и цифры 0–9, RFC 2938), длина 5–1024,
 * уникальность в календаре. Дефисы, «@», подчёркивания — запрещены, наш
 * UUID с дефисами давал 400 «Bad Request» на каждом создании. Формы:
 *  - UID уже base32hex-совместим (старые события из миграции — lower-hex,
 *    чужие идентификаторы Google) — едет как есть;
 *  - канонический UUID (новые события) → «tnote» + UUID без дефисов; на
 *    вычитке дефисы восстанавливаются по маске 8-4-4-4-12 — uid совпадает
 *    с базой;
 *  - прочее (носители заметок «note-…@…») → «tnote» + hex-код UTF-8 байтов;
 *    заметки сопоставляются по дате, но UID восстанавливается для
 *    tombstones и консистентности.
 */

private val icsDate = DateTimeFormatter.BASIC_ISO_DATE

/** RFC 3339 с местным смещением — формат времени Google. */
private val RFC3339: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

private const val MARKER = "tnote"
private val BASE32_HEX = Regex("[0-9a-v]+")
private val COMPACT_UUID = Regex("[0-9a-f]{32}")
private val UUID_DASH_POSITIONS = intArrayOf(8, 13, 18, 23)

/** Наши UID → допустимый для Google идентификатор события. */
fun googleEventIdFor(uid: String): String = when {
    uid.matches(BASE32_HEX) && uid.length in 5..1024 -> uid
    isCanonicalUuid(uid) -> MARKER + uid.replace("-", "")
    else -> MARKER + uid.toByteArray(Charsets.UTF_8).joinToString("") { byte -> "%02x".format(byte) }
}

/** Идентификатор события Google → наш UID; не наш id — как есть. */
fun googleUidFromEventId(id: String): String {
    if (!id.startsWith(MARKER)) return id
    val payload = id.substring(MARKER.length)
    return when {
        payload.matches(COMPACT_UUID) -> compactToUuid(payload)

        payload.length == 8 && payload.all { it.isDigit() } ->
            noteUidFor(payload)

        else -> decodeHex(payload) ?: id
    }
}

private fun isCanonicalUuid(uid: String): Boolean {
    if (uid.length != 36) return false
    UUID_DASH_POSITIONS.forEach { index -> if (uid[index] != '-') return false }
    return uid.replace("-", "").matches(COMPACT_UUID)
}

private fun compactToUuid(payload: String): String = buildString {
    // Границы групп в беzdефисной форме: 8-4-4-4-12 → срезы 8, 12, 16, 20.
    var start = 0
    intArrayOf(8, 12, 16, 20).forEach { index ->
        append(payload, start, index)
        append('-')
        start = index
    }
    append(payload.substring(start))
}

private fun decodeHex(payload: String): String? = runCatching {
    payload.chunked(2)
        .map { token -> token.toInt(16).toByte() }
        .toByteArray()
        .toString(Charsets.UTF_8)
}.getOrNull()?.takeIf { decoded -> decoded.all { it.code >= 32 } }

/**
 * VEVENT из события Google. null — событие не переносится в модель:
 * отменено (status=cancelled — это «удалено» для diff'а движка), без id
 * или без читаемой даты начала.
 */
fun googleEventToVEvent(json: JSONObject): VEvent? {
    if (json.optString("status") == "cancelled") return null
    val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return null
    val start = json.optJSONObject("start") ?: return null
    val end = json.optJSONObject("end")

    // Событие с точным временем: момент приводим к зоне устройства — встреча
    // показывается по местным часам, а её дата — по локальной дате того же
    // момента. dateTime без offset читается в зоне timeZone слота (см.
    // localMoment). Без времени — дневная метка, как была.
    val startMoment = localMoment(
        start.optString("dateTime").takeIf { it.isNotEmpty() },
        start.optString("timeZone").takeIf { it.isNotEmpty() },
    )
    val anchor = startMoment?.toLocalDate() ?: googleDay(start) ?: return null

    return VEvent().apply {
        // UID — наш, восстановленный из id Google: вычитка сопоставляет
        // события с базой по нему.
        properties.add(Uid(googleUidFromEventId(id)))
        if (startMoment != null) {
            properties.add(DtStart(icsDateTime(anchor, startMoment.hour * 60 + startMoment.minute)))
            // Конец — свой момент со своей датой: событие может длиться
            // через полночь. Вседневный конец у события с временем — разнороден
            // и не переносится.
            val endZone = end?.optString("timeZone")?.takeIf { it.isNotEmpty() }
            end?.optString("dateTime")?.takeIf { it.isNotEmpty() }
                ?.let { localMoment(it, endZone) }
                ?.let { moment ->
                    properties.add(DtEnd(icsDateTime(moment.toLocalDate(), moment.hour * 60 + moment.minute)))
                }
        } else {
            properties.add(DtStart(Date(anchor.format(icsDate), "yyyyMMdd")))
            val endDate: LocalDate? = when {
                end == null -> null

                end.optString("date").isNotEmpty() -> googleDay(end)

                // Точный момент конца → эксклюзивная дата следующего дня (см. KDoc).
                else -> googleDay(end)?.plusDays(1)
            }
            endDate?.let { properties.add(DtEnd(Date(it.format(icsDate), "yyyyMMdd"))) }
        }
        json.optString("summary").takeIf { it.isNotEmpty() }?.let { properties.add(Summary(it)) }
        json.optString("description").takeIf { it.isNotEmpty() }?.let { properties.add(Description(it)) }
        rfc3339ToMillis(json.optString("updated"))?.let { properties.add(LastModified(DateTime(it))) }
        json.optJSONArray("recurrence")?.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("RRULE") -> properties.add(RRule(line.substringAfter(':')))

                line.startsWith("EXDATE") -> properties.add(exDateFrom(line))

                // RDATE/прочее нашей моделью не выражается — игнорируем,
                // как в CalDAV-разборе (правило важнее выдумывания).
                else -> Unit
            }
        }
    }
}

/**
 * id серии, чьим вхождением (исключением) является этот объект, или null.
 * Non-null у отменённых и перенесённых вхождений повторяющихся событий:
 * отдельным событием такое жить не должно (мастер разворачивает вхождение
 * сам — получил бы дубль), а PUT по мастеру перечеркнул бы исключение.
 */
internal fun JSONObject.seriesExceptionParentId(): String? = optString("recurringEventId").takeIf { it.isNotEmpty() }

/**
 * День, в который мастер серии разворачивает это вхождение, — «исходный»
 * слот: originalStartTime (Google даёт его и перенесённым, и отменённым
 * экземплярам), а без него — собственное начало экземпляра. Вычитка
 * примешивает эти дни к exdates мастера: вхождение отменено или перенесено,
 * и развёртка его, как живого, показала бы фантом в прежнем слоте. Новое
 * время перенесённого вхождения моделью невыразимо — день просто выпадает
 * из развёртки (полное решение — хранение исходного JSON — покажет и его).
 */
internal fun JSONObject.originalOccurrenceDay(): LocalDate? {
    val slot = optJSONObject("originalStartTime") ?: optJSONObject("start") ?: return null
    return localMoment(
        slot.optString("dateTime").takeIf { it.isNotEmpty() },
        slot.optString("timeZone").takeIf { it.isNotEmpty() },
    )?.toLocalDate() ?: googleDay(slot)
}

/**
 * Есть ли в событии Google поля, которых нет в модели T-Note. Наш экспорт
 * отправляет полный ресурс (PUT), и сервер стёр бы всё перечисленное:
 * описание, набранное человеком (наш PUT перезаписывает description целиком —
 * маркером [T-Note] у событий или текстом заметки у носителей «✍️»), место,
 * участники, вложения, видеоконференция, «источник», цвет,
 * видимость/прозрачность не по умолчанию, гостевые права, напоминания
 * (включая явное «без напоминаний»), расширенные свойства, служебные типы
 * событий (вне офиса, фокус) и правила повторяемости вне RRULE/EXDATE
 * (RDATE и прочее).
 *
 * [descriptionIsOwn] — description является payload'ом самого объекта: у
 * события-носителя заметки это текст заметки (NoteIcsMapper), который экспорт
 * воспроизводит дословно, поэтому признаком чужих данных он не служит.
 */
internal fun JSONObject.hasUnmodeledContent(descriptionIsOwn: Boolean = false): Boolean {
    // Текст человека вокруг нашего маркера (или вместо него): PUT затёр бы его
    // служебной строкой. Та же проверка, что у CalDAV-ресурса, — закон один.
    if (!descriptionIsOwn && descriptionHasTextBeyondMarker(optString("description"))) return true
    if (optString("location").isNotBlank()) return true
    if (optJSONArray("attendees")?.length() ?: 0 > 0) return true
    if (optJSONArray("attachments")?.length() ?: 0 > 0) return true
    if (has("conferenceData") || optString("hangoutLink").isNotEmpty()) return true
    optJSONObject("source")?.let { source ->
        if (source.optString("title").isNotBlank() || source.optString("url").isNotBlank()) return true
    }
    if (optJSONObject("extendedProperties")?.let { it.has("private") || it.has("shared") } == true) return true
    val reminders = optJSONObject("reminders")
    if (reminders != null) {
        // И нестандартные overrides, и явное «без напоминаний» (useDefault=false
        // с пустым списком) — выбор человека: наш PUT reminders не отправляет,
        // и Google вернул бы событию напоминания по умолчанию календаря.
        if (!reminders.optBoolean("useDefault", true)) return true
        if (reminders.optJSONArray("overrides")?.length() ?: 0 > 0) return true
    }
    if (optString("colorId").isNotBlank() || optString("color").isNotBlank()) return true
    // Значения по умолчанию Google не отдаёт; всё, что пришло явно и не
    // дефолт, — выбор человека, теряемый при перезаписи.
    if (optString("visibility").isNotEmpty() && !optString("visibility").equals("default", ignoreCase = true)) return true
    if (optString("transparency").isNotEmpty() && !optString("transparency").equals("opaque", ignoreCase = true)) return true
    if (optString("eventType").isNotEmpty() && !optString("eventType").equals("legacy", ignoreCase = true)) return true
    if (optBoolean("guestsCanModify")) return true
    if (has("guestsCanCancel") && !getBoolean("guestsCanCancel")) return true
    if (has("guestsCanInviteOthers") && !getBoolean("guestsCanInviteOthers")) return true
    if (has("guestsCanSeeOtherGuests") && !getBoolean("guestsCanSeeOtherGuests")) return true
    optJSONArray("recurrence")?.let { rules ->
        for (index in 0 until rules.length()) {
            val line = rules.optString(index).trim()
            // Модель воспроизводит RRULE и EXDATE; прочее (RDATE, EXRULE)
            // при PUT пропало бы.
            if (!line.startsWith("RRULE") && !line.startsWith("EXDATE")) return true
        }
    }
    return false
}

/** Событие Google из VEVENT (наш экспорт): только те поля, что Google хранит. */
fun veventToGoogleJson(vevent: VEvent): JSONObject {
    val json = JSONObject()
    val uid = vevent.properties.byName(Property.UID)?.value.orEmpty()
    json.put("id", googleEventIdFor(uid))
    vevent.properties.byName(Property.SUMMARY)?.value?.takeIf { it.isNotEmpty() }?.let { json.put("summary", it) }
    vevent.properties.byName(Property.DESCRIPTION)?.value?.takeIf { it.isNotEmpty() }?.let { json.put("description", it) }
    (vevent.properties.byName(Property.DTSTART) as? DtStart)?.let { dtStart ->
        // Событие со временем едет как dateTime с местным смещением — так
        // Google понимает его; без времени — как вседневное (date), где конец
        // эксклюзивный, как и у RFC 5545.
        val moment = (dtStart.getDate() as? DateTime)?.let { localMomentOf(it) }
        if (moment != null) {
            json.put("start", JSONObject().put("dateTime", rfc3339Of(moment)))
        } else {
            googleDayValue(dtStart.value)?.let { json.put("start", JSONObject().put("date", it.toString())) }
        }
    }
    (vevent.properties.byName(Property.DTEND) as? DtEnd)?.let { dtEnd ->
        val moment = (dtEnd.getDate() as? DateTime)?.let { localMomentOf(it) }
        if (moment != null) {
            json.put("end", JSONObject().put("dateTime", rfc3339Of(moment)))
        } else {
            // Эксклюзивная дата конца — ровно как у date-формы Google.
            googleDayValue(dtEnd.value)?.let { json.put("end", JSONObject().put("date", it.toString())) }
        }
    }
    // created/updated — read-only у Google (сами проставит); X-TNOTE-* он
    // не хранит — отделка едет маркером в description, как у Яндекса.
    val recurrence = JSONArray()
    // Годовое правило дня рождения получает явные селекторы месяца и дня:
    // голый FREQ=YEARLY Google принимает не во всех формах, а для 29 февраля
    // документировано «последний день февраля» (BYMONTHDAY=-1) — та же дата,
    // которую модель показывает в невисокосные годы.
    val allDayStart = (vevent.properties.byName(Property.DTSTART) as? DtStart)
        ?.takeIf { it.getDate() !is DateTime }
        ?.let { googleDayValue(it.value) }
    // Property.toString() рендерится с хвостовым переводом строки —
    // строки recurrence обязаны быть чистыми.
    vevent.properties.filterIsInstance<RRule>().forEach { recurrence.put(googleRule(it, allDayStart)) }
    vevent.properties.filterIsInstance<ExDate>().forEach { recurrence.put(it.toString().trim()) }
    if (recurrence.length() > 0) json.put("recurrence", recurrence)
    return json
}

/**
 * RRULE для Google. Наш день рождения — единственное событие с голым
 * FREQ=YEARLY: у повторений экспорт всегда пишет INTERVAL. Для годового
 * all-day правила Google получает явные BYMONTH/BYMONTHDAY, а 29 февраля —
 * BYMONTHDAY=-1 («последний день февраля»), ровно как модель показывает
 * такой день рождения в невисокосные годы.
 */
private fun googleRule(rule: RRule, allDayStart: LocalDate?): String {
    val value = rule.value.trim()
    if (value != "FREQ=YEARLY" || allDayStart == null) return "RRULE:$value"
    val monthDay = if (allDayStart.monthValue == 2 && allDayStart.dayOfMonth == 29) {
        -1
    } else {
        allDayStart.dayOfMonth
    }
    return "RRULE:FREQ=YEARLY;BYMONTH=${allDayStart.monthValue};BYMONTHDAY=$monthDay"
}

/**
 * Строка свойства recurrence Google («RRULE:FREQ=…», «EXDATE;VALUE=DATE:…»,
 * «EXDATE:20261130T100000») в наш EXDATE-объект; неразборчивые даты
 * пропускаются. Форма зависит от содержимого: дневные значения — DATE-список
 * (параметр обязательный), значения со временем — DATE-TIME, приведённый к
 * местным часам устройства (Google отдаёт и «naive», и «…Z»; параметр TZID
 * в префиксе строки не читаем — модель и так живёт в зоне устройства).
 * Время исключения модели не нужно (хранится дата), но терять сам день
 * из-за незнакомой формы нельзя.
 */
private fun exDateFrom(line: String): ExDate {
    val tokens = line.substringAfter(':').split(',').map { raw -> raw.trim() }.filter { it.isNotEmpty() }
    val timed = tokens.any { raw -> raw.contains('T') }
    val list = net.fortuna.ical4j.model.DateList(
        if (timed) net.fortuna.ical4j.model.parameter.Value.DATE_TIME else net.fortuna.ical4j.model.parameter.Value.DATE,
    )
    tokens.forEach { raw ->
        val moment = if (raw.contains('T')) runCatching { DateTime(raw) }.getOrNull()?.let(::localMomentOf) else null
        val day = moment?.toLocalDate() ?: googleDayValue(raw) ?: return@forEach
        list.add(
            if (timed) {
                icsDateTime(day, moment?.let { local -> local.hour * 60 + local.minute } ?: 0)
            } else {
                Date(day.format(icsDate), "yyyyMMdd")
            },
        )
    }
    // VALUE=DATE уже приходит из DateList(Value.DATE): ExDate копирует тип
    // списка в параметры. Явное добавление дублировало параметр
    // («VALUE=DATE;VALUE=DATE») и делало EXDATE невалидным.
    return ExDate(list)
}

/**
 * «2026-11-20» / «2026-11-20T10:00:00+03:00» → LocalDate (дата в таймзоне
 * события). Неразборчивое — null.
 */
private fun googleDay(obj: JSONObject): LocalDate? = googleDayValue(
    obj.optString("date").takeIf { it.isNotEmpty() } ?: obj.optString("dateTime"),
)

/**
 * Строка времени Google («2026-11-20T10:00:00+03:00», RFC 3339) → местный
 * момент устройства. Смещение в самой строке старше поля [timeZone]: момент
 * однозначен и приводится к зоне устройства (встреча показывается по часам
 * пользователя).
 *
 * dateTime без offset — форма, разрешённая контрактом API (для повторяющихся
 * событий Google её и требует: «include timeZone if you include recurrence»):
 * смысл стенного времени задаёт IANA-имя в поле timeZone слота — момент
 * переводится из зоны события в зону устройства, иначе встреча в другой
 * стране показывалась бы по чужим часам, а у значений около полуночи
 * сдвигалась бы и дата. Без timeZone (или с нечитаемым именем) стенное
 * время сохраняется как есть — зона устройства.
 */
private fun localMoment(value: String?, timeZone: String? = null): LocalDateTime? {
    if (value.isNullOrEmpty()) return null
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull()
        ?.let { return LocalDateTime.ofInstant(it, ZoneId.systemDefault()) }
    val naive = runCatching { LocalDateTime.parse(value) }.getOrNull() ?: return null
    val zone = timeZone?.takeIf { it.isNotEmpty() }
        ?.let { name -> runCatching { ZoneId.of(name) }.getOrNull() }
        ?: return naive
    return naive.atZone(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
}

/**
 * Момент свойства ICS в зоне устройства. «Плавающее» время (наш экспект
 * без зоны) ical4j разбирает в зоне устройства, поэтому стенное время
 * сохраняется, а момент с UTC/TZID переводится в местный.
 */
private fun localMomentOf(date: DateTime): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.time), ZoneId.systemDefault())

/** Местный момент в форме RFC 3339 — как ждёт Google (смещение обязательно). */
private fun rfc3339Of(moment: LocalDateTime): String = moment.atZone(ZoneId.systemDefault()).format(RFC3339)

private fun googleDayValue(value: String?): LocalDate? {
    if (value.isNullOrEmpty()) return null
    // Три формы: ISO «2026-11-20» (JSON Google), базовая «20261120»
    // (значения свойств ical4j на обратном ходе) и точное время
    // «2026-11-20T09:00:00+03:00» (читаем по местной дате события).
    return runCatching { LocalDate.parse(value) }.getOrNull()
        ?: runCatching { LocalDate.parse(value, icsDate) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
}

/** RFC 3339 («…T10:00:00.000Z») → epoch millis; неразборчивое — null. */
private fun rfc3339ToMillis(value: String): Long? =
    value.takeIf { it.isNotEmpty() }?.let { raw -> runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() }

/** Строчный обход JSONArray без скастовывания к Iterable<Any?>. */
private inline fun JSONArray.forEachLine(block: (String) -> Unit) {
    for (index in 0 until length()) {
        val item = optString(index)
        if (item.isNotEmpty()) block(item)
    }
}

/**
 * Ссылка объекта для [ru.taurlom.tnote.data.sync.CaldavApi] в форме
 * «<calendarId>/<eventId>»: оба сегмента адресуют запросы Google API
 * (calendarId не содержит «/»), href строки хранятся в БД и переживают
 * перезапуски.
 */
fun splitResourceUrl(url: String): Pair<String, String>? {
    val index = url.lastIndexOf('/')
    if (index <= 0 || index == url.length - 1) return null
    return url.substring(0, index) to url.substring(index + 1)
}
