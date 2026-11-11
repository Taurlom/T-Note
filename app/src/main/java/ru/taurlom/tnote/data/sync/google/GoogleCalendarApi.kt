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
import org.json.JSONObject
import ru.taurlom.tnote.data.sync.CaldavApi
import ru.taurlom.tnote.data.sync.CaldavPull
import ru.taurlom.tnote.data.sync.CaldavRemoteEvent
import ru.taurlom.tnote.data.sync.CaldavWriteResponse
import ru.taurlom.tnote.data.sync.byName
import ru.taurlom.tnote.data.sync.hasTnoteMetadata
import ru.taurlom.tnote.data.sync.noteBySummary
import ru.taurlom.tnote.data.sync.noteText
import ru.taurlom.tnote.data.sync.parseIcs
import ru.taurlom.tnote.data.sync.toScheduledEvent
import ru.taurlom.tnote.data.sync.vevents
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import java.io.IOException
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
 *  - «создать, только если нет» (If-None-Match:*) = POST insert; «объект
 *    уже есть» API отвечает 409/400 — возвращается движку как 412, его
 *    конфликтный путь (перечитка, LWW) работает без изменений;
 *    If-Match Google не проверяет — окно потерянной правки закрывается
 *    LWW по меткам времени;
 *  - отменённые события (status=cancelled) из вычитки не выдаются: движок
 *    увидит их отсутствие и удалит строки сам; DELETE несуществующего
 *    объекта Google отвечает 404/410 — обе формы для движка «и так нет»;
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
        .build()

    override suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef> {
        val json = getJson("$BASE/users/me/calendarList", username)
        val items = json.optJSONArray("items") ?: return emptyList()
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
        val events = mutableListOf<CaldavRemoteEvent>()
        val notes = mutableListOf<CaldavRemoteEvent>()
        var unreadable = 0
        var pageToken: String? = null
        do {
            val url = buildString {
                append(BASE).append("/calendars/").append(urlSegment(collectionUrl))
                append("/events?maxResults=2500&showDeleted=true")
                pageToken?.let { append("&pageToken=").append(it) }
            }
            val json = getJson(url, username)
            val items = json.optJSONArray("items")
            if (items == null && json.optString("error").isNotEmpty()) {
                throw CaldavSyncException.Server("Google не отдал список событий")
            }
            if (items != null) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val vevent = googleEventToVEvent(item) ?: continue
                    val mapped = vevent.toScheduledEvent() ?: run {
                        unreadable++
                        continue
                    }
                    val href = objectHref(collectionUrl, mapped.uid)
                    val remote = CaldavRemoteEvent(
                        href = href,
                        etag = item.optString("etag").takeIf { it.isNotEmpty() },
                        event = mapped,
                        metadataFromIcs = vevent.hasTnoteMetadata(),
                        noteText = if (noteBySummary(mapped.title)) vevent.noteText() else null,
                    )
                    if (noteBySummary(mapped.title)) notes += remote else events += remote
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return CaldavPull(events = events, notes = notes, unreadable = unreadable)
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
        val response = request(method, url, username, json)
        // Коды движения: 2xx — успех; «идентификатор занят» при первой
        // публикации — 412 (движок перечитает и решит LWW); 5xx — временный
        // сбой, движок отложит строку до следующего прогона; прочие 4xx —
        // постоянный отказ: наружу с телом и названием события, карточка
        // аккаунта покажет причину (молча-деферить 400 нельзя — повторится
        // вечно; так нашлась и причина 400 — дефисы в id).
        when {
            response.code in 200..299 -> Unit

            createIfAbsent && etag == null && response.code == 409 ->
                return CaldavWriteResponse(412, null)

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

    override suspend fun deleteEvent(url: String, username: String, password: String): Int {
        val (calendarId, eventId) = splitResourceUrl(url)
            ?: throw CaldavSyncException.Server("Нечитаемая ссылка объекта: $url")
        val response = request(
            "DELETE",
            "$BASE/calendars/${urlSegment(calendarId)}/events/$eventId",
            username,
            null,
        )
        // 410 Gone («уже отменено») для движка неотличимо от «и так нет»;
        // 5xx — временный сбой (Deferred); прочие 4xx — постоянный отказ
        // с телом наружу.
        return when {
            response.code == 410 -> 404

            response.code in 200..299 || response.code == 404 -> response.code

            response.code in 500..599 -> response.code

            else -> throw CaldavSyncException.Server(
                "Google не удалил событие (HTTP ${response.code}): ${response.body.take(200)}",
            )
        }
    }

    override suspend fun pullEvent(url: String, username: String, password: String): CaldavRemoteEvent? {
        val (calendarId, eventId) = splitResourceUrl(url)
            ?: throw CaldavSyncException.Server("Нечитаемая ссылка объекта: $url")
        val response = request(
            "GET",
            "$BASE/calendars/${urlSegment(calendarId)}/events/$eventId",
            username,
            null,
        )
        return when {
            response.code == 404 || response.code == 410 -> null

            response.code !in 200..299 -> throw CaldavSyncException.Server(
                "HTTP ${response.code}: ${response.body.take(160)}",
            )

            else -> {
                val json = parseJson(response.body)
                    ?: throw CaldavSyncException.Server("Неразборчивый ответ Google")
                val vevent = googleEventToVEvent(json)
                    ?: throw CaldavSyncException.Server("Событие Google не разбирается")
                val event = vevent.toScheduledEvent() ?: return null
                CaldavRemoteEvent(
                    href = url,
                    etag = json.optString("etag").takeIf { it.isNotEmpty() },
                    event = event,
                    metadataFromIcs = vevent.hasTnoteMetadata(),
                    noteText = if (noteBySummary(event.title)) vevent.noteText() else null,
                )
            }
        }
    }

    override fun objectHref(collectionUrl: String, uid: String): String = "$collectionUrl/${googleEventIdFor(uid)}"

    /** Один HTTP-запрос с Bearer-токеном; тело — JSON или null. */
    private data class RawResponse(val code: Int, val body: String)

    private suspend fun request(method: String, url: String, accountName: String, body: JSONObject?): RawResponse =
        withContext(Dispatchers.IO) {
            var token = tokenSource.token(accountName)
            var response = call(method, url, token, body)
            // Просроченный кеш токена чинится на месте: сброс и один повтор.
            if (response.code == 401) {
                runCatching { GoogleAuthUtil.clearToken(context, token) }
                token = tokenSource.token(accountName)
                response = call(method, url, token, body)
            }
            response
        }

    private fun call(method: String, url: String, token: String, body: JSONObject?): RawResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "T-Note/1.0 GoogleCalendar")
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

    private fun urlSegment(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val BASE = "https://www.googleapis.com/calendar/v3"
        val WRITABLE_ROLES = setOf("owner", "writer")
    }
}
