package ru.taurlom.tnote.data.sync

import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.taurlom.tnote.domain.model.CaldavCalendarRef
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import javax.inject.Inject
import javax.inject.Singleton

/** Этап вычитки: разобранные события и число нечитаемых тел. */
data class CaldavPull(val events: List<ScheduledEvent>, val unreadable: Int = 0)

/**
 * Сетевые операции CalDAV — интерфейс, чтобы движок синхронизации
 * тестировался без сети (поставка фейка в CaldavSyncEngineTest).
 */
interface CaldavApi {

    /** principal → calendar-home-set → список календарей с VEVENT. */
    suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef>

    /** Полная вычитка коллекции: события без X-свойств (Яндекс их режет). */
    suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull
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
        val events = mutableListOf<ScheduledEvent>()
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
            // Одно событие-объект может вернуть и мастер, и его overrides;
            // на этапе 1 храним мастера, RECURRENCE-ID достанет этап 2.
            events += calendar.vevents().mapNotNull { it.toScheduledEvent() }
        }
        return CaldavPull(events, unreadable)
    }

    /** href относительный (/calendars/…%40…) → абсолютный URL. */
    private fun resolve(base: String, href: String): String = base.toHttpUrl().resolve(href)?.toString()
        ?: throw CaldavSyncException.Server("Нечитаемый href от сервера: $href")
}
