package ru.taurlom.tnote.data.sync

import okhttp3.HttpUrl.Companion.toHttpUrl
import ru.taurlom.tnote.data.security.originOf
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
    /**
     * Записи вычитки, которые пришлись «не сюда»: тело без calendar-data,
     * неразборчивый ICS, VEVENT без UID/DTSTART, событие Google без id или
     * даты. Считать их удалёнными на сервере нельзя — они просто не прочлись,
     * поэтому число и решает, полон ли снапшот ([complete]).
     */
    val unreadable: Int = 0,
    /**
     * UID'ы серверных объектов, которые не вписаны в список, но и перезаписи
     * не терпят: вхождения повторяющихся серий Google (у них свой id, а
     * содержимое живёт в мастер-событии). Строка базы с таким UID — наследие
     * более старой версии приложения, и PUT вернул бы на сервер дубль
     * вхождения или отменённый экземпляр.
     */
    val protectedUids: Set<String> = emptySet(),
) {

    /**
     * «Всё, что есть на сервере»: разница «чего на сервере нет» (и связанных
     * с ней локальных удалений) применима только к полному снапшоту.
     */
    val complete: Boolean get() = unreadable == 0
}

/**
 * Точечное чтение одного объекта (разрешение конфликта PUT): три исхода
 * вместо «null на всё». [NotFound] — сервер сказал 404/410, объект можно
 * пересоздавать; [Unreadable] — ответ пришёл, но прочесть его не удалось:
 * вывод «объекта нет» из него делать нельзя, иначе PUT поверх непрочитанной
 * серверной копии затрёт чужую правку.
 */
sealed interface CaldavObjectResult {

    data class Found(val event: CaldavRemoteEvent) : CaldavObjectResult

    object NotFound : CaldavObjectResult

    data class Unreadable(val reason: String) : CaldavObjectResult
}

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
     * Пришли ли в VEVENT наши метаданные: свойства X-TNOTE-* или маркер
     * в DESCRIPTION («[T-Note] icon=…», tnoteMarkerFrom). Экспорт всегда
     * пишет X-TNOTE-TYPE/ICON и маркер, поэтому полноценный CalDAV-сервер
     * возвращает их всегда, а Яндекс выбрасывает все неизвестные X-свойства
     * при PUT (проба 2026-11) — DESCRIPTION он хранит, так что маркер доехал;
     * без обоих каналов (чужое событие) при переписи движок сохраняет
     * локальные иконку/цвет/«скрыть прошлое» (applyRemote).
     */
    val metadataFromIcs: Boolean = true,
    /**
     * Текст заметки из DESCRIPTION — только у событий-носителей заметок
     * («✍️ …» в имени); у обычных событий null. Модель ScheduledEvent
     * описания не имеет, поэтому текст едет рядом, а движок ведёт такие
     * объекты в calendar_notes, минуя scheduled_events.
     */
    val noteText: String? = null,
    /**
     * Непустое DESCRIPTION у серверного объекта. Движку решает судьбу
     * бэкфилла маркера: у старых событий нашего экспорта description нет
     * вовсе (перезапись с добавлением маркера безопасна), текст же значит,
     * что объект наполнялся человеком — его PUT не переписывает.
     */
    val hasDescription: Boolean = false,
    /**
     * Серверный объект нельзя пересобирать из нашей модели: в нём есть
     * данные, которых в модели нет (место, участники, напоминания, чужие
     * свойства, DESCRIPTION человека, невыразимое RRULE) или рядом с мастером
     * лежат исключения серии (RECURRENCE-ID). PUT одним VEVENT из модели
     * стёр бы их с сервера, поэтому движок такие строки не отправляет:
     * локальные правки остаются локальными, объект живёт своей жизнью.
     */
    val notReproducible: Boolean = false,
)

/**
 * Сетевые операции CalDAV — интерфейс, чтобы движок синхронизации
 * тестировался без сети (поставка фейка в CaldavSyncEngineTest).
 */
interface CaldavApi {

    /** principal → calendar-home-set → список календарей с VEVENT. */
    suspend fun discoverCalendars(server: String, username: String, password: String): List<CaldavCalendarRef>

    /**
     * Полная вычитка коллекции: события без X-свойств (Яндекс их режет).
     * Ответ, не похожий на календарный, — [CaldavSyncException.Server]:
     * движок обязан получить либо полный список, либо отказ прогона, но не
     * «пустоту», которую он прочитал бы как удаление всех серверных объектов.
     */
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
     * DELETE строки-удаления: с [etag] — условный (If-Match), сервер отвечает
     * 412, если объект правился после нашей вычитки, — движок перечитает и
     * разрешит конфликт удаления с правкой; без ETag — безусловный. 404 —
     * «и так нет», тоже успех. Код движку — он решает, что дальше.
     */
    suspend fun deleteEvent(url: String, username: String, password: String, etag: String? = null): Int

    /**
     * Точечный PROPFIND depth 0 по одному объекту: событие вместе с адресом
     * и ETag — разрешение конфликта PUT (412/404) без вычитки всей коллекции.
     * «Объекта нет» и «объект не прочитался» — разные исходы ([CaldavObjectResult]):
     * первый позволяет пересоздать ресурс, второй не позволяет ничего решать.
     */
    suspend fun pullEvent(url: String, username: String, password: String): CaldavObjectResult

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
        val principal = requireFound(
            http.davRequest(
                "PROPFIND",
                principalUrl,
                "0",
                PRINCIPAL_BODY,
                username,
                password,
            ),
            "Запрос principal",
        )
        val homeHref = principal.firstOrNull()?.calendarHomes?.firstOrNull()
            ?: throw CaldavSyncException.Server("Сервер не отдал calendar-home-set")
        val homeUrl = resolve(principalUrl, homeHref)

        val listing = requireFound(
            http.davRequest(
                "PROPFIND",
                homeUrl,
                "1",
                LIST_BODY,
                username,
                password,
            ),
            "Список календарей",
        )
        return listing
            .filter { it.isCalendar && it.supportsVevents }
            .map { CaldavCalendarRef(it.displayName ?: homeUrl, resolve(homeUrl, it.href)) }
            .distinctBy { it.url }
    }

    override suspend fun pullEvents(collectionUrl: String, username: String, password: String): CaldavPull {
        val entries = requireFound(
            http.davRequest(
                "REPORT",
                collectionUrl,
                "1",
                QUERY_BODY,
                username,
                password,
            ),
            "Вычитка коллекции",
        )
        var unreadable = 0
        val events = mutableListOf<CaldavRemoteEvent>()
        val notes = mutableListOf<CaldavRemoteEvent>()
        for (entry in entries) {
            // Запись без тела. Кроме коллекций: их Depth 1 перечисляет
            // наряду с объектами, а calendar-data у них нет по определению
            // (href календарного объекта — файл, он не кончается слэшем).
            // Объект же без тела — это объект, который мы не прочитали:
            // снапшот перестаёт быть полным.
            val data = entry.calendarData
            if (data == null) {
                if (!entry.href.trimEnd().endsWith("/")) unreadable++
                continue
            }
            val href = resolve(collectionUrl, entry.href)
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
            //
            // Ресурс разбирается атомарно: строкой живёт мастер серии, а
            // исключения (VEVENT с RECURRENCE-ID) — это не самостоятельные
            // объекты (UID тот же, две записи перезаписывали бы одну строку
            // в порядке компонентов). Их наличие лишь помечает объект
            // «не перезаписывать»: PUT одним VEVENT стёр бы все overrides.
            val vevent = calendar.recurrenceMaster()
            val parsed = vevent?.toScheduledEvent()
            if (vevent == null || parsed == null) {
                // Ни разбираемого мастера, ни обычного VEVENT в ресурсе нет
                // (только исключения, тело не VEVENT, VEVENT без UID/DTSTART):
                // для разницы по UID объект всё равно что исчез, а он может
                // быть жив — снапшот перестаёт быть полным.
                unreadable++
                continue
            }
            // Вхождения, перечёркнутые исключениями (отменённые и перенесённые),
            // подавляются в развёртке мастера: объект заморожен
            // (hasRecurrenceOverrides → notReproducible), инъекция визуальная
            // и на сервер не уезжает (см. recurrenceOverrideDays).
            val mapped = parsed.copy(exdates = parsed.exdates + calendar.recurrenceOverrideDays())
            // Явные метки (X-*/маркер) объявляют объект своим: ✍ в имени
            // тогда не маркер заметки, а пользовательский текст (isNoteCarrier).
            val metadataFromIcs = vevent.hasTnoteMetadata()
            val isNote = isNoteCarrier(mapped.title, metadataFromIcs)
            // Заметка едет своим списком: маркер — ✍ в починенном имени.
            val remote = CaldavRemoteEvent(
                href = href,
                etag = entry.etag,
                event = mapped,
                metadataFromIcs = metadataFromIcs,
                noteText = if (isNote) vevent.noteText() else null,
                hasDescription = vevent.hasDescriptionText(),
                notReproducible = calendar.hasRecurrenceOverrides() ||
                    vevent.hasUnmodeledContent(descriptionIsOwn = isNote),
            )
            if (isNote) notes += remote else events += remote
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

    override suspend fun deleteEvent(url: String, username: String, password: String, etag: String?): Int = http.writeRequest(
        method = "DELETE",
        url = url,
        body = null,
        contentType = null,
        username = username,
        password = password,
        // Условное удаление: сервер правился после нашей вычитки — 412,
        // conflict'ом решит движок (caldav DELETE принимает If-Match).
        ifMatch = etag,
        ifNoneMatchStar = false,
    ).code

    override suspend fun pullEvent(url: String, username: String, password: String): CaldavObjectResult {
        val outcome = http.davRequest(
            "PROPFIND",
            url,
            "0",
            OBJECT_BODY,
            username,
            password,
            // 404 — «объект уже удалён»: только этот исход даёт движку
            // право пересоздать ресурс; прочие «пустые» ответы таковыми
            // не считаются (см. [CaldavObjectResult.Unreadable]).
            notFoundIsEmpty = true,
        )
        return when (outcome) {
            DavOutcome.NotFound -> CaldavObjectResult.NotFound
            is DavOutcome.ProtocolError -> CaldavObjectResult.Unreadable(outcome.message)
            is DavOutcome.Found -> readObject(outcome.entries, url)
        }
    }

    /**
     * Ответ точечного PROPFIND в один объект; всё, что не прочлось, —
     * [CaldavObjectResult.Unreadable] с причиной (её видит карточка аккаунта
     * через отложенную строку очереди).
     */
    private fun readObject(entries: List<DavEntry>, url: String): CaldavObjectResult {
        val entry = entries.firstOrNull { it.calendarData != null }
            ?: return CaldavObjectResult.Unreadable("Объект без calendar-data: $url")
        val data = entry.calendarData ?: return CaldavObjectResult.Unreadable("Объект без calendar-data: $url")
        val href = runCatching { resolve(url, entry.href) }.getOrNull()
            ?: return CaldavObjectResult.Unreadable("Нечитаемый href объекта: ${entry.href}")
        // XML-нормализация — та же, что в pullEvents: тела разворачиваем
        // в RFC-шной форме переводов строк для ical4j.
        val ics = data.replace("\r\n", "\n").lines().joinToString("\r\n")
        val calendar = parseIcs(ics) ?: return CaldavObjectResult.Unreadable("Нечитаемое тело объекта: $href")
        // Разбор — тот же, что у полной вычитки: мастер серии без
        // RECURRENCE-ID, а исключения помечают объект «не перезаписывать».
        val vevent = calendar.recurrenceMaster() ?: return CaldavObjectResult.Unreadable(
            if (calendar.vevents().isEmpty()) "В объекте нет VEVENT: $href" else "В объекте только исключения серии: $href",
        )
        val parsed = vevent.toScheduledEvent()
            ?: return CaldavObjectResult.Unreadable("VEVENT без UID/DTSTART: $href")
        // Тот же алгоритм, что у полной вычитки: перечёркнутые исключениями
        // вхождения подавляются в развёртке мастера.
        val event = parsed.copy(exdates = parsed.exdates + calendar.recurrenceOverrideDays())
        // Явные метки объявляют объект своим: ✍ в имени не маркер заметки
        // (isNoteCarrier) — тот же шлюз, что в полной вычитке.
        val metadataFromIcs = vevent.hasTnoteMetadata()
        val isNote = isNoteCarrier(event.title, metadataFromIcs)
        return CaldavObjectResult.Found(
            CaldavRemoteEvent(
                href = href,
                etag = entry.etag,
                event = event,
                metadataFromIcs = metadataFromIcs,
                noteText = if (isNote) vevent.noteText() else null,
                hasDescription = vevent.hasDescriptionText(),
                notReproducible = calendar.hasRecurrenceOverrides() ||
                    vevent.hasUnmodeledContent(descriptionIsOwn = isNote),
            ),
        )
    }

    override fun objectHref(collectionUrl: String, uid: String): String = deriveObjectHref(collectionUrl, uid)

    /**
     * href относительный (/calendars/…%40…) → абсолютный URL против адреса
     * запроса — сервер присылает их percent-кодированными (%40 вместо @).
     *
     * Абсолютный href ЧУЖОГО origin отклоняется: по этому адресу движок
     * отправит и следующую вычитку, и PUT/DELETE с паролем в Authorization,
     * то есть один такой ответ переселял бы подписку на чужой сервер.
     */
    private fun resolve(base: String, href: String): String {
        val target = base.toHttpUrl().resolve(href)?.toString()
            ?: throw CaldavSyncException.Server("Нечитаемый href от сервера: $href")
        if (originOf(target) != originOf(base)) {
            throw CaldavSyncException.Server("Сервер отдал ссылку вне своего адреса: $href")
        }
        return target
    }

    /**
     * Ответ полной вычитки/разведки в список записей: всё, что не 207
     * multistatus, — отказ прогона. Применять разницу к «половине» серверных
     * данных опаснее, чем не синхронизироваться вовсе: следующий прогон
     * догонит, а стёртые строки — нет.
     */
    private fun requireFound(outcome: DavOutcome, what: String): List<DavEntry> = when (outcome) {
        is DavOutcome.Found -> outcome.entries
        DavOutcome.NotFound -> throw CaldavSyncException.Server("$what: сервер ответил 404")
        is DavOutcome.ProtocolError -> throw CaldavSyncException.Server("$what: ${outcome.message}")
    }
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
