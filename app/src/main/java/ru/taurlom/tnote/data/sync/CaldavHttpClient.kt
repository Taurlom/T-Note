package ru.taurlom.tnote.data.sync

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Запись multistatus (RFC 4918): href со status и вытащенные свойства.
 * Тела календарных объектов (calendar-data) возвращаются целым текстом —
 * их разбирает ical4j в [EventIcsMapper].
 */
internal data class DavEntry(
    val href: String = "",
    val status: Int = 0,
    val etag: String? = null,
    val displayName: String? = null,
    /** resourcetype содержит caldav:calendar — это календарь-коллекция. */
    val isCalendar: Boolean = false,
    /** supported-calendar-component-set умеет VEVENT. */
    val supportsVevents: Boolean = false,
    /** Найденный calendar-home-set (ответ principal-запроса). */
    val calendarHomes: List<String> = emptyList(),
    /** Тело объекта из calendar-data (ответ calendar-query). */
    val calendarData: String? = null,
)

/**
 * Итог простой записи (PUT/DELETE, этап 2): код состояния и заголовок ETag.
 * Multistatus здесь не ждут: 201/204 — успех, 412 — «конфликт, разрешает
 * движок», 404 — «объекта нет»; ETag сервер отдаёт не всегда — null
 * означает «предусловие на следующей правке не выставить», движок это
 * переживает через конфликтный путь If-None-Match:*.
 */
data class CaldavWriteResponse(val code: Int, val etag: String? = null)

/**
 * ETag в одной форме для всей очереди. Яндекс отдаёт значение без кавычек
 * (GET/REPORT — голое число), но If-Match признаёт только в цитированной
 * форме RFC 7232: пока хранили «как вернул сервер», каждое обновление
 * серверного события отвечало 412. Здесь всё приводится к кавычкам — так
 * If-Match уходит сразу в читаемой сервером форме.
 */
internal fun normalizeEtag(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return if (trimmed.startsWith("\"")) trimmed else "\"$trimmed\""
}

/**
 * Транспорт CalDAV: OkHttp с преждевременным BasicAuth и разбором
 * multistatus. Слой нарочно тонкий: ответы Яндекса простые (см. живые
 * пробы, зафиксированные в CaldavHttpClientTest), поэтому dav4jvm ради
 * одного сервера не тащим — по пути разбора XML у нас свои приоритеты
 * (юнит-тесты на реальных ответах).
 */
@Singleton
class CaldavHttpClient @Inject constructor() {

    private val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * PROPFIND / REPORT с телом [body]; ответ 207 разбирается в [DavEntry].
     * Отказ авторизации → [CaldavSyncException.Auth], сеть → [CaldavSyncException.Network],
     * прочие протокольные расхождения → [CaldavSyncException.Server].
     * [notFoundIsEmpty] — точечный PROPFIND при разрешении конфликтов:
     * 404 там означает «объект уже удалён», и вычитка отдельного объекта
     * отвечает пустым списком, а не исключением; всем прочим запросам 404
     * по-прежнему выстреливает как протокольный сбой.
     */
    internal suspend fun davRequest(
        method: String,
        url: String,
        depth: String,
        body: String,
        username: String,
        password: String,
        notFoundIsEmpty: Boolean = false,
    ): List<DavEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header(
                "Authorization",
                Credentials.basic(username, password, Charsets.UTF_8),
            )
            .header("Depth", depth)
            .header("User-Agent", "T-Note/1.0 CalDAV")
            .method(method, body.toRequestBody("application/xml".toMediaType()))
            .build()

        try {
            okhttp.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 401 || response.code == 403 ->
                        throw CaldavSyncException.Auth()

                    response.code == 404 && notFoundIsEmpty -> return@use emptyList<DavEntry>()

                    !response.isSuccessful ->
                        throw CaldavSyncException.Server(
                            "HTTP ${response.code}: ${text.take(160)}",
                        )
                }
                runCatching { DavXml.parseMultistatus(text) }
                    .getOrElse {
                        throw CaldavSyncException.Server(
                            "Неразборчивый ответ сервера (HTTP ${response.code})",
                        )
                    }
            }
        } catch (e: CaldavSyncException) {
            throw e
        } catch (e: IOException) {
            throw CaldavSyncException.Network(e)
        }
    }

    /**
     * PUT/DELETE без multistatus: ответ — код состояния и заголовок ETag.
     * Предусловия RFC 7232 задаёт движок: If-Match («обновляй, если сервер
     * не затирал объект») — для уже публиковавшегося события; If-None-Match: *
     * («создавай, только если объекта ещё нет») — для первой публикации.
     * Отказ авторизации и сеть — те же исключения, что у davRequest; прочие
     * коды возвращаются движку: 412 и 404 он трактует в рамках разрешения
     * конфликтов, остальные — отказ синхронизации.
     */
    internal suspend fun writeRequest(
        method: String,
        url: String,
        body: String?,
        contentType: String?,
        username: String,
        password: String,
        ifMatch: String?,
        ifNoneMatchStar: Boolean,
    ): CaldavWriteResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url.toHttpUrl())
            .header(
                "Authorization",
                Credentials.basic(username, password, Charsets.UTF_8),
            )
            .header("User-Agent", "T-Note/1.0 CalDAV")
            .method(
                method,
                body?.let { text ->
                    // Яндекс-квирк (пробы 2026-11): парсер text/calendar
                    // угадывает кодировку тела — без UTF-8 BOM декодирует
                    // как cp1252 и хранит кракозябры (charset=... он
                    // игнорирует во всех формах; единственный «сам»-UTF-8
                    // случай — байты из дыр cp1252, «А»/«с»/«я»/«Ё», то
                    // есть угадывание непредсказуемо). BOM включают UTF-8
                    // достоверно: верно и в веб-Яндексе, и у нас при
                    // вычитке.
                    val bytes = if (contentType?.startsWith("text/calendar") == true) {
                        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                            text.toByteArray(Charsets.UTF_8)
                    } else {
                        text.toByteArray(Charsets.UTF_8)
                    }
                    bytes.toRequestBody(contentType?.toMediaType())
                },
            )
        ifMatch?.let { value ->
            normalizeEtag(value)?.let { normalized -> builder.header("If-Match", normalized) }
        }
        if (ifNoneMatchStar) builder.header("If-None-Match", "*")

        try {
            okhttp.newCall(builder.build()).execute().use { response ->
                when (response.code) {
                    401, 403 -> throw CaldavSyncException.Auth()
                }
                CaldavWriteResponse(response.code, normalizeEtag(response.header("ETag")))
            }
        } catch (e: CaldavSyncException) {
            throw e
        } catch (e: IOException) {
            throw CaldavSyncException.Network(e)
        }
    }
}

/** Разбор multistatus вынесен отдельно, чтобы гонять его в юнит-тестах. */
internal object DavXml {

    private const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    private val statusRegex = Regex("HTTP/1\\.[01] (\\d+)")

    /** В каком теге лежит очередь текст: чтение «текущим приёмником». */
    private enum class Sink { NONE, ENTRY_HREF, HOME, STATUS, ETAG, DISPLAYNAME, CALENDAR_DATA }

    private enum class Scope { OUTSIDE, RESPONSE, PROPSTAT, PROP }

    fun parseMultistatus(xml: String): List<DavEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        val entries = mutableListOf<DavEntry>()

        var scope = Scope.OUTSIDE
        var sink = Sink.NONE
        var pendingText = StringBuilder()

        // Уровень response
        var entryHref = ""
        var entryEtag: String? = null
        var entryDisplayName: String? = null
        var entryIsCalendar = false
        var entryVevents = false
        val entryHomes = mutableListOf<String>()
        var entryCalendarData: String? = null

        // Уровень propstat: копятся локально и применяются только при 200.
        var propStatus = 200
        var propEtag: String? = null
        var propDisplayName: String? = null
        var propIsCalendar = false
        var propVevents = false
        val propHomes = mutableListOf<String>()
        var propCalendarData: String? = null

        var inResourceType = false
        var inHomeSet = false
        var inCompSet = false

        fun finishEntry() {
            entries += DavEntry(
                href = entryHref,
                status = propStatus,
                etag = entryEtag,
                displayName = entryDisplayName,
                isCalendar = entryIsCalendar,
                supportsVevents = entryVevents,
                calendarHomes = entryHomes.toList(),
                calendarData = entryCalendarData,
            )
        }

        fun resetEntry() {
            entryHref = ""
            entryEtag = null
            entryDisplayName = null
            entryIsCalendar = false
            entryVevents = false
            entryHomes.clear()
            entryCalendarData = null
        }

        fun resetPropstat() {
            propStatus = 200
            propEtag = null
            propDisplayName = null
            propIsCalendar = false
            propVevents = false
            propHomes.clear()
            propCalendarData = null
        }

        fun commitPropstat() {
            // Свойства применяются только из благополучных propstat.
            if (propStatus != 200) return
            entryEtag = propEtag
            entryDisplayName = propDisplayName
            entryIsCalendar = propIsCalendar
            entryVevents = propVevents
            entryHomes.clear()
            entryHomes.addAll(propHomes)
            entryCalendarData = propCalendarData
        }

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break

            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when (name) {
                        "multistatus" -> scope = Scope.OUTSIDE

                        "response" -> {
                            scope = Scope.RESPONSE
                            resetEntry()
                        }

                        "propstat" -> {
                            scope = Scope.PROPSTAT
                            resetPropstat()
                        }

                        "prop" -> scope = Scope.PROP

                        "resourcetype" -> inResourceType = scope == Scope.PROP

                        "calendar" ->
                            if (inResourceType && parser.namespace == NS_CALDAV) {
                                propIsCalendar = true
                            }

                        "supported-calendar-component-set" -> inCompSet = scope == Scope.PROP

                        "comp" ->
                            if (inCompSet &&
                                parser.getAttributeValue(null, "name") == "VEVENT"
                            ) {
                                propVevents = true
                            }

                        "calendar-home-set" -> inHomeSet = scope == Scope.PROP

                        "calendar-data" ->
                            if (scope == Scope.PROP) sink = Sink.CALENDAR_DATA

                        "href" -> sink = when {
                            scope == Scope.PROP && inHomeSet -> Sink.HOME
                            scope == Scope.RESPONSE || scope == Scope.PROP -> Sink.ENTRY_HREF
                            else -> Sink.NONE
                        }

                        "status" ->
                            sink = if (scope == Scope.PROPSTAT || scope == Scope.PROP) {
                                Sink.STATUS
                            } else {
                                Sink.NONE
                            }

                        "getetag" -> if (scope == Scope.PROP) sink = Sink.ETAG

                        "displayname" -> if (scope == Scope.PROP) sink = Sink.DISPLAYNAME

                        else -> sink = Sink.NONE
                    }
                    pendingText = StringBuilder()
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (sink != Sink.NONE) {
                    pendingText.append(parser.text)
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    val text = pendingText.toString()
                    when (name) {
                        "href" -> when (sink) {
                            Sink.HOME -> propHomes += text.trim()
                            Sink.ENTRY_HREF -> entryHref = text.trim()
                            else -> Unit
                        }

                        "status" ->
                            propStatus = statusRegex.find(text.trim())
                                ?.groupValues?.get(1)?.toIntOrNull() ?: 200

                        "getetag" -> propEtag = normalizeEtag(text)

                        "displayname" -> propDisplayName = text.trim()

                        "calendar-data" -> propCalendarData = text

                        "calendar-home-set" -> inHomeSet = false

                        "resourcetype" -> inResourceType = false

                        "supported-calendar-component-set" -> inCompSet = false

                        "prop" -> scope = Scope.PROPSTAT

                        "propstat" -> {
                            scope = Scope.RESPONSE
                            commitPropstat()
                        }

                        "response" -> {
                            scope = Scope.OUTSIDE
                            finishEntry()
                        }
                    }
                    sink = Sink.NONE
                    pendingText = StringBuilder()
                }
            }
        }
        return entries
    }
}
