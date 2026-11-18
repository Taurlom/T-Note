package ru.taurlom.tnote.data.sync.google

import com.google.android.gms.auth.GoogleAuthUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.fortuna.ical4j.model.Property
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.taurlom.tnote.data.sync.CaldavApi
import ru.taurlom.tnote.data.sync.CaldavObjectResult
import ru.taurlom.tnote.data.sync.CaldavPull
import ru.taurlom.tnote.data.sync.CaldavRemoteEvent
import ru.taurlom.tnote.data.sync.CaldavWriteResponse
import ru.taurlom.tnote.data.sync.byName
import ru.taurlom.tnote.data.sync.hasTnoteMetadata
import ru.taurlom.tnote.data.sync.isNoteCarrier
import ru.taurlom.tnote.data.sync.normalizeEtag
import ru.taurlom.tnote.data.sync.noteText
import ru.taurlom.tnote.data.sync.parseIcs
import ru.taurlom.tnote.data.sync.toScheduledEvent
import ru.taurlom.tnote.data.sync.vevents
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Транспорт Google Calendar API v3 за тем же интерфейсом [CaldavApi], что
 * и CalDAV-клиент Яндекса: движок синхронизации не знает о провайдере.
 *
 * Соответствие понятий:
 *  - collectionUrl аккаунта = calendarId Google;
 *  - href объекта (серверная ссылка строки) = «<calendarId>/<eventId>» —
 *    [splitResourceUrl] разбирает её в аргументы запросов;
 *  - идентификатор события Google = наш UID ([googleEventIdFor]) — API
 *    разрешает клиентский id, повторная публикация того же UID переписывает
 *    свой объект, как в CalDAV;
 *  - username = адрес Google-аккаунта (ключ OAuth-токена), password —
 *    не используется (место заглушки ради общей сигнатуры);
 *  - «создать, только если нет» = POST insert: условного создания Google
 *    не поддерживает, но вставка по занятому id отвечает 409/400 —
 *    возвращается движку как 412, его конфликтный путь (перечитка, LWW)
 *    работает без изменений; обновление по известному ETag идёт с If-Match
 *    — Google проверяет предусловие и при несовпадении отвечает 412
 *    (conditional modification, «Get specific versions of resources»), так
 *    что правка другого устройства между вычиткой и PUT не затирается
 *    вслепую; без ETag PUT уходит безусловно — это окно закрывает
 *    перечитка объекта движком после принятой записи (refetchEtag);
 *  - отменённые события (status=cancelled) из вычитки не выдаются: движок
 *    увидит их отсутствие и удалит строки сам; DELETE несуществующего
 *    объекта Google отвечает 404/410 — обе формы для движка «и так нет»;
 *  - вхождения серий (объекты с recurringEventId — перенесённые и отменённые
 *    вхождения) в базу не попадают вовсе: экземпляр уже разворачивает мастер,
 *    а отдельная строка была бы дублем. Их UID'ы уходят в protectedUids —
 *    наследные строки прежних версий приложения нельзя отправлять обратно;
 *    серия с такими объектами помечается [CaldavRemoteEvent.notReproducible] —
 *    PUT по мастеру перечеркнул бы исключения, поэтому её правки остаются
 *    локальными. Дни отменённых и перенесённых вхождений примешиваются к
 *    exdates мастера, чтобы развёртка не показывала их живыми; новое время
 *    перенесённого вхождения моделью невыразимо — это остаётся за полным
 *    решением с хранением исходного JSON. Исключения учитываются по всему
 *    списку, а не постранично: мастер и его вхождение могут попасть на
 *    разные страницы;
 *  - вычитка обязана быть списком: ответ без «items» и без верного «kind»
 *    — отказ прогона, а не «пустой календарь», иначе движок стёр бы всё
 *    локальное по пустому ответу прокси; нечитаемые items (без id, start,
 *    даты) помечают снапшот неполным — см. [CaldavPull.complete];
 *  - синхронизация полная (без syncToken): Яндекс её тоже не даёт, движок
 *    ведёт полный diff, у личных календарей это сотни объектов.
 *
 * Согласие OAuth: первый токен для аккаунта требует подтверждения
 * пользователя — [GoogleTokenSource] бросает [CaldavConsentRequired]
 * с намерением; экран подключения его запускает, фоновые прогоны
 * с выданным согласием проходят молча. Просроченный кеш токена
 * (ответ 401) чинится очисткой токена и одним повтором запроса.
 */
@Singleton
class GoogleCalendarApi @Inject constructor(
    private val tokenSource: GoogleTokenSource,
    @ApplicationContext private val context: android.content.Context,
) : CaldavApi {

    private val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Bearer-токен не должен уезжать по редиректу: адреса API фиксированы
        // (BASE), 3xx здесь — случай нештатный.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef> {
        val json = getJson("$BASE/users/me/calendarList", username)
        val items = listItems(json, KIND_CALENDAR_LIST, "список календарей") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                if (item.optBoolean("deleted", false)) continue
                // Право записи обязательно: подписка двусторонняя, календари
                // «только просмотр» (reader/freeBusyReader) не предлагаются.
                if (item.optString("accessRole") !in WRITABLE_ROLES) continue
                val id = item.optString("id").takeIf { it.isNotEmpty() } ?: continue
                val summary = item.optString("summary").takeIf { it.isNotEmpty() } ?: id
                add(CaldavCalendarRef(name = summary, url = id))
            }
        }
    }

    override suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull {
        // Страницы накапливаются целиком до разбора: исключения серии могут
        // попасть на другую страницу, чем её мастер, и постраничная обработка
        // не увидела бы признака «у серии есть исключения» (мастер остался бы
        // размороженным). Память — тот же staging, что у движка: календарь
        // личный, сотни-тысячи объектов.
        val items = mutableListOf<JSONObject?>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(BASE).append("/calendars/").append(urlSegment(collectionUrl))
                // singleEvents закреплён явно: при false (серверное умолчание)
                // приходят мастера серий, а не развёрнутые вхождения.
                // showDeleted=true обязателен: отменённые вхождения серий
                // Google отдаёт объектами со status=cancelled — без него
                // отменённое вхождение исчезло бы из списка бесследно.
                append("/events?maxResults=2500&showDeleted=true&singleEvents=false")
                pageToken?.let { append("&pageToken=").append(it) }
            }
            val json = getJson(url, username)
            // Успешный events.list всегда несёт конверт списка («kind»:
            // «calendar#events»); ни items, ни конверта — это не вычитка, а
            // пустой ответ (прокси-заглушка, обрезанный ответ). Как «пустой
            // календарь» он не годится: движок стёр бы все локальные строки.
            val pageItems = listItems(json, KIND_EVENTS, "список событий")
            if (pageItems != null) {
                for (index in 0 until pageItems.length()) {
                    items += pageItems.optJSONObject(index)
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return googlePullFromItems(collectionUrl, items)
    }

    override suspend fun putEvent(
        url: String,
        ics: String,
        username: String,
        password: String,
        etag: String?,
        createIfAbsent: Boolean,
    ): CaldavWriteResponse {
        val (calendarId, eventId) = splitResourceUrl(url)
            ?: throw CaldavSyncException.Server("Нечитаемая ссылка объекта: $url")
        val vevent = parseIcs(ics)?.vevents()?.singleOrNull()
            ?: throw CaldavSyncException.Server("Тело события не разобралось")
        val json = veventToGoogleJson(vevent)
        val method = if (createIfAbsent && etag == null) "POST" else "PUT"
        val url = if (method == "POST") {
            "$BASE/calendars/${urlSegment(calendarId)}/events"
        } else {
            "$BASE/calendars/${urlSegment(calendarId)}/events/$eventId"
        }
        // Условная правка (RFC 7232 conditional modification): PUT несёт
        // If-Match по ETag из последней вычитки — Google сверяет его с
        // текущей версией события и при несовпадении отвечает 412, не
        // применяя запись: вписанная другим устройством между вычиткой и
        // отправкой правка не затирается вслепую. Для insert предусловий
        // нет (Google их не поддерживает) — там занятость id даёт 409.
        val ifMatch = if (method == "PUT") googleIfMatch(etag) else null
        val response = request(method, url, username, json, ifMatch)
        // Коды движения: 2xx — успех; «идентификатор занят» при первой
        // публикации и несовпавший If-Match на обновлении — 412 (движок
        // перечитает и решит LWW); 5xx — временный сбой, движок отложит
        // строку до следующего прогона; прочие 4xx — постоянный отказ:
        // наружу с телом и названием события, карточка аккаунта покажет
        // причину (молча-деферить 400 нельзя — повторится вечно; так
        // нашлась и причина 400 — дефисы в id).
        when {
            response.code in 200..299 -> Unit

            createIfAbsent && etag == null && response.code == 409 ->
                return CaldavWriteResponse(412, null)

            response.code == 412 -> return CaldavWriteResponse(412, null)

            response.code in 500..599 -> return CaldavWriteResponse(response.code, null)

            else -> throw CaldavSyncException.Server(
                buildString {
                    append("Google не принял событие (").append(method)
                    append(" ").append(response.code).append(")")
                    vevent.properties.byName(Property.SUMMARY)?.value
                        ?.let { append(" «").append(it).append("»") }
                    append(": ").append(response.body.take(160))
                },
            )
        }
        val responseEtag = runCatching { JSONObject(response.body).optString("etag") }
            .getOrNull()?.takeIf { it.isNotEmpty() }
        return CaldavWriteResponse(response.code, responseEtag)
    }

    override suspend fun deleteEvent(url: String, username: String, password: String, etag: String?): Int {
        val (calendarId, eventId) = splitResourceUrl(url)
            ?: throw CaldavSyncException.Server("Нечитаемая ссылка объекта: $url")
        // Удаление, как и правка, условно по имеющемуся ETag: Google
        // проверяет If-Match и на DELETE (conditional modification —
        // «Get specific versions of resources»), при несовпадении отвечает
        // 412 — движок перечитает объект и решит конфликт удаления с правкой.
        val response = request(
            "DELETE",
            "$BASE/calendars/${urlSegment(calendarId)}/events/$eventId",
            username,
            null,
            googleIfMatch(etag),
        )
        // 410 Gone («уже отменено») для движка неотличимо от «и так нет»;
        // 412 — конфликт, его разрешение на движке; 5xx — временный сбой;
        // прочие 4xx — постоянный отказ с телом наружу.
        return when {
            response.code == 410 -> 404

            response.code in 200..299 || response.code == 404 || response.code == 412 -> response.code

            response.code in 500..599 -> response.code

            else -> throw CaldavSyncException.Server(
                "Google не удалил событие (HTTP ${response.code}): ${response.body.take(200)}",
            )
        }
    }

    override suspend fun pullEvent(url: String, username: String, password: String): CaldavObjectResult {
        val (calendarId, eventId) = splitResourceUrl(url)
            ?: throw CaldavSyncException.Server("Нечитаемая ссылка объекта: $url")
        val response = request(
            "GET",
            "$BASE/calendars/${urlSegment(calendarId)}/events/$eventId",
            username,
            null,
        )
        return when {
            // 404/410 — «объекта нет» (410 Google отвечает на уже удалённый
            // id): единственный исход, при котором движку можно пересоздавать.
            response.code == 404 || response.code == 410 -> CaldavObjectResult.NotFound

            // Временный сбой или неожиданный код: о судьбе объекта он ничего
            // не говорит, строка ждёт следующего прогона.
            response.code !in 200..299 -> CaldavObjectResult.Unreadable(
                "HTTP ${response.code}: ${response.body.take(160)}",
            )

            else -> {
                val json = parseJson(response.body)
                    ?: return CaldavObjectResult.Unreadable("Неразборчивый ответ Google")
                // Вхождение серии (перенесённое или отменённое) — не
                // самостоятельный объект: PUT по нему был бы правкой мастера.
                if (json.seriesExceptionParentId() != null) {
                    return CaldavObjectResult.Unreadable("Это вхождение серии, а не объект: $eventId")
                }
                // Отменённое событие для diff'а движка то же, что удалённое.
                if (json.optString("status") == "cancelled") return CaldavObjectResult.NotFound
                val vevent = googleEventToVEvent(json)
                    ?: return CaldavObjectResult.Unreadable("Событие Google не разбирается")
                val event = vevent.toScheduledEvent()
                    ?: return CaldavObjectResult.Unreadable("VEVENT Google без UID/DTSTART")
                // Явные метки (маркер DESCRIPTION) объявляют объект своим:
                // ✍ в имени не маркер заметки, а пользовательский текст
                // (isNoteCarrier) — тот же шлюз, что у CalDAV-канала.
                val metadataFromIcs = vevent.hasTnoteMetadata()
                val isNote = isNoteCarrier(event.title, metadataFromIcs)
                CaldavObjectResult.Found(
                    CaldavRemoteEvent(
                        href = url,
                        etag = json.optString("etag").takeIf { it.isNotEmpty() },
                        event = event,
                        metadataFromIcs = metadataFromIcs,
                        noteText = if (isNote) vevent.noteText() else null,
                        hasDescription = json.optString("description").isNotBlank(),
                        notReproducible = json.hasUnmodeledContent(descriptionIsOwn = isNote),
                    ),
                )
            }
        }
    }

    override fun objectHref(collectionUrl: String, uid: String): String = googleObjectHref(collectionUrl, uid)

    /** Ответ простого запроса: код и тело. */
    private data class RawResponse(val code: Int, val body: String)

    /** Один HTTP-запрос с Bearer-токеном; тело — JSON или null; ifMatch — условная правка (If-Match). */
    private suspend fun request(
        method: String,
        url: String,
        accountName: String,
        body: JSONObject?,
        ifMatch: String? = null,
    ): RawResponse = withContext(Dispatchers.IO) {
        var token = tokenSource.token(accountName)
        var response = call(method, url, token, body, ifMatch)
        // Просроченный кеш токена чинится на месте: сброс и один повтор.
        if (response.code == 401) {
            runCatching { GoogleAuthUtil.clearToken(context, token) }
            token = tokenSource.token(accountName)
            response = call(method, url, token, body, ifMatch)
        }
        response
    }

    private fun call(method: String, url: String, token: String, body: JSONObject?, ifMatch: String?): RawResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "T-Note/1.0 GoogleCalendar")
        ifMatch?.let { builder.header("If-Match", it) }
        if (body != null) {
            val payload = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.method(method, payload)
        } else {
            builder.method(method, null)
        }
        try {
            okhttp.newCall(builder.build()).execute().use { response ->
                return RawResponse(response.code, response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw CaldavSyncException.Network(e)
        }
    }

    private suspend fun getJson(url: String, accountName: String): JSONObject {
        val response = request("GET", url, accountName, null)
        if (response.code == 404 || response.code == 410) {
            throw CaldavSyncException.Server("Календарь не найден (HTTP ${response.code})")
        }
        if (response.code !in 200..299) {
            throw CaldavSyncException.Server("HTTP ${response.code}: ${response.body.take(160)}")
        }
        return parseJson(response.body)
            ?: throw CaldavSyncException.Server("Неразборчивый ответ Google")
    }

    private fun parseJson(text: String): JSONObject? = runCatching { JSONObject(text) }.getOrNull()

    /**
     * Массив «items» успешного списочного ответа Google. Вернулся массив —
     * как есть; массива нет, но конверт ресурса верный — Google так отдаёт
     * пустой список (null); прочее — ответ не списка ([CaldavSyncException.Server],
     * то есть отказ прогона: пустым календарём его считать нельзя).
     */
    private fun listItems(json: JSONObject, kind: String, what: String): JSONArray? {
        json.optJSONArray("items")?.let { return it }
        if (json.optString("kind") != kind) {
            throw CaldavSyncException.Server("Google не отдал $what")
        }
        return null
    }

    private fun urlSegment(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val BASE = "https://www.googleapis.com/calendar/v3"
        const val KIND_EVENTS = "calendar#events"
        const val KIND_CALENDAR_LIST = "calendar#calendarList"
        val WRITABLE_ROLES = setOf("owner", "writer")
    }
}

/**
 * Значение If-Match по RFC 7232 — в цитированной форме. Google отдаёт
 * «etag» в теле уже с кавычками (normalizeEtag их не удваивает). Пустое
 * значение или слабый валидатор («W/...») — предусловия нет: PUT уходит
 * безусловно (слабый ETag Google в If-Match не принимает — отвечал бы
 * 412 вечно), свежий ETag вернёт перечитка объекта движком.
 */
internal fun googleIfMatch(etag: String?): String? {
    val trimmed = etag?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed.startsWith("W/")) return null
    return normalizeEtag(trimmed)
}

/** Адрес публикации события Google: «<calendarId>/<eventId>» (см. [splitResourceUrl]). */
internal fun googleObjectHref(collectionUrl: String, uid: String): String = "$collectionUrl/${googleEventIdFor(uid)}"

/**
 * Разбор накопленных items вычитки в staging [CaldavPull]. Два прохода:
 * исключения серий (объекты с recurringEventId — перенесённые и отменённые
 * вхождения) учитываются до мастеров независимо от порядка в списке и от
 * границ страниц.
 *
 * Первый проход: вхождение серии не попадает в базу — экземпляр разворачивает
 * мастер, отдельная строка была бы дублем; его UID уходит в protectedUids
 * (наследную строку прежней версии приложения нельзя отправлять обратно —
 * PUT вернул бы на сервер дубль или отменённый экземпляр); id серии
 * запоминается «с исключениями» (мастер замёрзнет: PUT пересобрал бы
 * recurrence и перечеркнул исключения), а день исходного вхождения
 * ([originalOccurrenceDay]) — в подавление развёртки мастера.
 *
 * Второй проход: мастера и одиночные события. Дни отменённых и перенесённых
 * вхождений примешиваются к exdates мастера — приложение не показывает их
 * живыми; инъекция визуальная (серия заморожена, на сервер она не уезжает, а
 * при исчезновении исключений следующая вычитка перепишет строку чистым
 * мастером). Отменённый мастер пропускается — для diff'а движка это «удалён».
 * Нечитаемые items (null, без id/start/даты) делают снапшот неполным.
 */
internal fun googlePullFromItems(collectionUrl: String, items: List<JSONObject?>): CaldavPull {
    val events = mutableListOf<CaldavRemoteEvent>()
    val notes = mutableListOf<CaldavRemoteEvent>()
    val exceptionUids = mutableSetOf<String>()
    val seriesWithExceptions = mutableSetOf<String>()
    val suppressedDays = mutableMapOf<String, MutableSet<LocalDate>>()
    var unreadable = 0
    for (item in items) {
        val parentId = item?.seriesExceptionParentId() ?: continue
        item.optString("id")
            .takeIf { it.isNotEmpty() }
            ?.let { id -> exceptionUids += googleUidFromEventId(id) }
        seriesWithExceptions += parentId
        item.originalOccurrenceDay()?.let { day ->
            suppressedDays.getOrPut(parentId) { mutableSetOf() } += day
        }
    }
    for (item in items) {
        if (item == null) {
            // Элемент списка не объект: прочесть его нечем — за удалённый
            // выдавать нельзя, снапшот неполон.
            unreadable++
            continue
        }
        // Вхождение серии — учтено первым проходом, самостоятельной строкой
        // быть не может.
        if (item.seriesExceptionParentId() != null) continue
        // Отменённый мастер — это «удалено»: его отсутствия в списке движок
        // ждать и должен, полноте оно не мешает.
        if (item.optString("status") == "cancelled") continue
        val vevent = googleEventToVEvent(item)
        if (vevent == null) {
            // Без id/start или с нечитаемой датой: объект на сервере есть,
            // а прочесть его нечем — за удалённый выдавать нельзя.
            unreadable++
            continue
        }
        val mapped = vevent.toScheduledEvent()
        if (mapped == null) {
            unreadable++
            continue
        }
        // Явные метки (маркер DESCRIPTION) объявляют объект своим: ✍ в имени
        // не маркер заметки, а пользовательский текст (isNoteCarrier).
        val metadataFromIcs = vevent.hasTnoteMetadata()
        val isNote = isNoteCarrier(mapped.title, metadataFromIcs)
        val suppressed = suppressedDays[item.optString("id")]
        val event = if (suppressed.isNullOrEmpty()) {
            mapped
        } else {
            mapped.copy(exdates = mapped.exdates + suppressed)
        }
        val remote = CaldavRemoteEvent(
            href = googleObjectHref(collectionUrl, event.uid),
            etag = item.optString("etag").takeIf { it.isNotEmpty() },
            event = event,
            metadataFromIcs = metadataFromIcs,
            noteText = if (isNote) vevent.noteText() else null,
            // Google кладёт текст в поле description — без этого флага
            // бэкфилл маркера считал бы описание пустым и затёр бы его при
            // первой же отделке локальной строки.
            hasDescription = item.optString("description").isNotBlank(),
            notReproducible = item.hasUnmodeledContent(descriptionIsOwn = isNote) ||
                seriesWithExceptions.contains(item.optString("id")),
        )
        if (isNote) notes += remote else events += remote
    }
    return CaldavPull(events = events, notes = notes, unreadable = unreadable, protectedUids = exceptionUids)
}
