package ru.taurlom.tnote.data.sync

import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Этап вычитки: разобранные объекты (с адресом и ETag) и число нечитаемых тел. */
data class CaldavPull(
    val events: List<CaldavRemoteEvent>,
    /** События-носители заметок («✍️ …» в имени) — движок ведёт их в calendar_notes. */
    val notes: List<CaldavRemoteEvent> = emptyList(),
    val unreadable: Int = 0,
)

/**
 * Объект с сервера в транспортной обёртке: разобранное событие плюс
 * адрес ресурса и его ETag. Этап 2 ведёт diff по UID и разрешает конфликты
 * через If-Match — обоим этим механизмам нужны href и ETag, поэтому клиент
 * отдаёт их вместе с разбором, а не теряет на границе слоёв.
 */
data class CaldavRemoteEvent(
    /** Абсолютный адрес ресурса — по нему ходят PUT/DELETE движка. */
    val href: String,
    val etag: String?,
    val event: ScheduledEvent,
    /**
     * Пришли ли в VEVENT наши свойства X-TNOTE-*. Экспорт всегда пишет
     * X-TNOTE-TYPE/ICON, поэтому полноценный CalDAV-сервер возвращает их
     * всегда, а Яндекс выбрасывает все неизвестные X-свойства при PUT
     * (проба 2026-11) — его копии приходят без метаданных, и при переписи
     * движок сохраняет локальные иконку/цвет/«скрыть прошлое» (applyRemote),
     * вместо того чтобы стирать их дефолтами уже на следующей вычитке.
     */
    val metadataFromIcs: Boolean = true,
    /**
     * Текст заметки из DESCRIPTION — только у событий-носителей заметок
     * («✍️ …» в имени); у обычных событий null. Модель ScheduledEvent
     * описания не имеет, поэтому текст едет рядом, а движок ведёт такие
     * объекты в calendar_notes, минуя scheduled_events.
     */
    val noteText: String? = null,
)

/**
 * Сетевые операции CalDAV — интерфейс, чтобы движок синхронизации
 * тестировался без сети (поставка фейка в CaldavSyncEngineTest).
 */
interface CaldavApi {

    /** principal → calendar-home-set → список календарей с VEVENT. */
    suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef>

    /** Полная вычитка коллекции: события без X-свойств (Яндекс их режет). */
    suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull

    /**
     * PUT календарного объекта: создаёт или обновляет ресурс на [url].
     * [etag] — If-Match для обновления уже публиковавшегося события
     * («не затри чужую правку, сделанную после нашей вычитки»);
     * [createIfAbsent] — If-None-Match: * для первой публикации своего
     * события. Возвращает код сервера и его ETag (если отдал).
     */
    suspend fun putEvent(
        url: String,
        ics: String,
        username: String,
        password: String,
        etag: String?,
        createIfAbsent: Boolean,
    ): CaldavWriteResponse

    /**
     * DELETE без предусловий (RFC 7232 If-Match на DELETE для tombstone не
     * даёт практичной выгоды, а 412-танцы усложняют редкий сценарий): 404 —
     * «и так нет», тоже успех. Код движку — он решает, что дальше.
     */
    suspend fun deleteEvent(url: String, username: String, password: String): Int

    /**
     * Точечный PROPFIND depth 0 по одному объекту: событие вместе с адресом
     * и ETag — разрешение конфликта PUT (412/404) без вычитки всей коллекции.
     * null — объекта нет (404) либо тело не разбирается.
     */
    suspend fun pullEvent(url: String, username: String, password: String): CaldavRemoteEvent?

    /** Адрес публикации события, ещё не гулявшего по серверу: <uid>.ics. */
    fun objectHref(collectionUrl: String, uid: String): String
}

// Тела PROPFIND/REPORT: «богатство» — намеренно минимум из проб сервера.

private const val PRINCIPAL_BODY =
    "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">" +
        "<d:prop><d:resourcetype/><c:calendar-home-set/></d:prop>" +
        "</d:propfind>"

private const val LIST_BODY =
    "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">" +
        "<d:prop><d:resourcetype/><d:displayname/>" +
        "<c:supported-calendar-component-set/></d:prop>" +
        "</d:propfind>"

private const val QUERY_BODY =
    "<c:calendar-query xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">" +
        "<d:prop><d:getetag/><c:calendar-data/></d:prop>" +
        "<c:filter><c:comp-filter name=\"VCALENDAR\">" +
        "<c:comp-filter name=\"VEVENT\"/></c:comp-filter></c:filter>" +
        "</c:calendar-query>"

/** Точечный PROPFIND одного объекта: ETag для предусловий + тело для LWW. */
private const val OBJECT_BODY =
    "<d:propfind xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">" +
        "<d:prop><d:getetag/><c:calendar-data/></d:prop>" +
        "</d:propfind>"

/**
 * Реализация: пути principal/home-set — по документации Яндекса
 * (/principals/users/{логин}/, /calendars/{логин}/), href из ответов
 * всегда разрешаются против URL запроса — сервер их присылает
 * percent-кодированными (%40 вместо @).
 */
@Singleton
class CaldavClient @Inject constructor(private val http: CaldavHttpClient) : CaldavApi {

    override suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef> {
        val principalUrl = "${server.trimEnd('/')}/principals/users/$username/"
        val principal = http.davRequest(
            "PROPFIND",
            principalUrl,
            "0",
            PRINCIPAL_BODY,
            username,
            password,
        )
        val homeHref = principal.firstOrNull()?.calendarHomes?.firstOrNull()
            ?: throw CaldavSyncException.Server("Сервер не отдал calendar-home-set")
        val homeUrl = resolve(principalUrl, homeHref)

        val listing = http.davRequest(
            "PROPFIND",
            homeUrl,
            "1",
            LIST_BODY,
            username,
            password,
        )
        return listing
            .filter { it.isCalendar && it.supportsVevents }
            .map { CaldavCalendarRef(it.displayName ?: homeUrl, resolve(homeUrl, it.href)) }
            .distinctBy { it.url }
    }

    override suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull {
        val entries = http.davRequest(
            "REPORT",
            collectionUrl,
            "1",
            QUERY_BODY,
            username,
            password,
        )
        var unreadable = 0
        val events = mutableListOf<CaldavRemoteEvent>()
        val notes = mutableListOf<CaldavRemoteEvent>()
        for (entry in entries) {
            val data = entry.calendarData ?: continue
            // XML-нормализация сжимает CRLF до LF; возвращаем RFC-шный вид,
            // чтобы ical4j разворачивал folded-строки как в реальных телах.
            val ics = data.replace("\r\n", "\n").lines().joinToString("\r\n")
            val calendar = parseIcs(ics)
            if (calendar == null) {
                unreadable++
                continue
            }
            // href и ETag объекта едут вместе с событием: движок ведёт diff
            // по UID, а предусловия PUT'а и DELETE'я не обходятся без них.
            val href = resolve(collectionUrl, entry.href)
            // Одно событие-объект может вернуть и мастер, и его overrides;
            // храним мастера: ровно то, что показывал read-only этап 1.
            calendar.vevents().mapNotNull { vevent ->
                val mapped = vevent.toScheduledEvent() ?: return@mapNotNull null
                // Заметка едет своим списком: маркер — ✍ в починенном имени.
                if (noteBySummary(mapped.title)) {
                    notes += CaldavRemoteEvent(
                        href = href,
                        etag = entry.etag,
                        event = mapped,
                        metadataFromIcs = vevent.hasTnoteMetadata(),
                        noteText = vevent.noteText(),
                    )
                } else {
                    events += CaldavRemoteEvent(
                        href = href,
                        etag = entry.etag,
                        event = mapped,
                        metadataFromIcs = vevent.hasTnoteMetadata(),
                    )
                }
                Unit
            }
        }
        return CaldavPull(events, notes, unreadable)
    }

    override suspend fun putEvent(
        url: String,
        ics: String,
        username: String,
        password: String,
        etag: String?,
        createIfAbsent: Boolean,
    ): CaldavWriteResponse = http.writeRequest(
        method = "PUT",
        url = url,
        body = ics,
        // RFC 6638: календарные объекты посылаются как text/calendar.
        contentType = "text/calendar",
        username = username,
        password = password,
        // Предусловия взаимоисключающие: обновление с известным ETag —
        // против чужой правки после нашей вычитки; у первой публикации
        // никакого «чужого» быть не должно — If-None-Match:*.
        ifMatch = etag,
        ifNoneMatchStar = createIfAbsent && etag == null,
    )

    override suspend fun deleteEvent(url: String, username: String, password: String): Int = http.writeRequest(
        method = "DELETE",
        url = url,
        body = null,
        contentType = null,
        username = username,
        password = password,
        ifMatch = null,
        ifNoneMatchStar = false,
    ).code

    override suspend fun pullEvent(url: String, username: String, password: String): CaldavRemoteEvent? {
        val entries = http.davRequest(
            "PROPFIND",
            url,
            "0",
            OBJECT_BODY,
            username,
            password,
            // 404 — «объект уже удалён»: движок различает это в конфликтном
            // разрешении, всей коллекции оно не касается.
            notFoundIsEmpty = true,
        )
        val entry = entries.firstOrNull { it.calendarData != null }
        val data = entry?.calendarData ?: return null
        // XML-нормализация — та же, что в pullEvents: тела разворачиваем
        // в RFC-шной форме переводов строк для ical4j.
        val ics = data.replace("\r\n", "\n").lines().joinToString("\r\n")
        // Тело с overrides принимается как и в pullEvents — мастером.
        val calendar = parseIcs(ics) ?: return null
        val vevent = calendar.vevents().firstOrNull() ?: return null
        val event = vevent.toScheduledEvent() ?: return null
        return CaldavRemoteEvent(
            href = resolve(url, entry.href),
            etag = entry.etag,
            event = event,
            metadataFromIcs = vevent.hasTnoteMetadata(),
            noteText = if (noteBySummary(event.title)) vevent.noteText() else null,
        )
    }

    override fun objectHref(collectionUrl: String, uid: String): String = deriveObjectHref(collectionUrl, uid)

    /** href относительный (/calendars/…%40…) → абсолютный URL. */
    private fun resolve(base: String, href: String): String = base.toHttpUrl().resolve(href)?.toString()
        ?: throw CaldavSyncException.Server("Нечитаемый href от сервера: $href")
}

/**
 * Адрес первой публикации события — коллекция + «<uid>.ics»: сервер принимает
 * PUT на любой href внутри коллекции и называет объект тем, что мы попросили.
 * UID кодируется в href-форму URL: URLEncoder даёт форму для query-строк
 * (пробелы «+»), для пути их правят на проценты.
 */
fun deriveObjectHref(collectionUrl: String, uid: String): String {
    val encoded = URLEncoder.encode(uid, Charsets.UTF_8.name()).replace("+", "%20")
    return collectionUrl.trimEnd('/') + "/" + encoded + ".ics"
}
