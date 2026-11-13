package ru.taurlom.tnote.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

enum class ScheduledEventType { REGULAR, BIRTHDAY, REPEATING, WEEKEND }

/**
 * Частота повторения — прямой аналог FREQ из RRULE (RFC 5545):
 * каждый день / каждую неделю / каждый месяц / каждый год.
 */
enum class RepeatUnit { DAYS, WEEKS, MONTHS, YEARS }

/**
 * Иконка обычного события. Отображается под числом дня в сетке календаря
 * и в списке событий дня. День рождения всегда рисуется подарком и
 * иконку не выбирает.
 */
enum class EventIcon {
    NOTE,
    TRAVEL,
    FOREST,
    GIFTS,
    HOME,
    HEALTH,
    BEACH,
    FOOTPRINT,
    CELEBRATION,
    BUILD,
    FITNESS,
    GROUPS,
    DELIVERY,
    SELF_CARE,
    BAR,
    GARDEN,
    FAVORITE,
    SETTINGS,
    PETS,
    KEEP,
    CALL,
    SHOPPING_CART,
    SCHOOL,
    MARTIAL_ARTS,
    CHESS_QUEEN,
    PALETTE,
}

/**
 * Календарное событие: обычное, день рождения или повторяющееся.
 *
 * [date] — якорная дата в формате "yyyy-MM-dd"; для дня рождения год
 * значения не имеет, используются только месяц и день. Для повторяющегося
 * события [date] — первое вхождение, от которого считаются повторы.
 */
data class ScheduledEvent(
    val id: Long = 0,

    /**
     * Глобальный UID для CalDAV-синхронизации (RFC 5545 UID): неизменен на
     * всю жизнь события и обязателен для сопоставления «наш ↔ серверный»
     * объект. Генерируется при создании; у событий из бэкапов восстановление
     * сохраняет исходный UID.
     */
    val uid: String = UUID.randomUUID().toString(),
    val date: String,
    val title: String,
    val type: ScheduledEventType = ScheduledEventType.REGULAR,
    val icon: EventIcon = EventIcon.NOTE,
    /** ARGB-цвет иконки; [DEFAULT_COLOR] — цвет из темы приложения. */
    val colorArgb: Long = DEFAULT_COLOR,

    /**
     * Частота повторения «каждые [repeatInterval] [repeatUnit]»; null —
     * не повторяется (для типа REPEATING обязательна).
     */
    val repeatUnit: RepeatUnit? = null,
    /** Сколько единиц частоты между вхождениями; для REPEATING не меньше 1. */
    val repeatInterval: Int = 1,
    /**
     * Дни недели для частоты WEEKS (аналог BYDAY); пустой набор — день
     * недели якоря. В других частотах не используется.
     */
    val repeatWeekdays: Set<DayOfWeek> = emptySet(),
    /**
     * Последний день повторения включительно ("yyyy-MM-dd"); null —
     * бессрочно. Аналог UNTIL из RRULE.
     */
    val repeatUntil: String? = null,
    /**
     * Отменённые вхождения повторения (аналог EXDATE из RRULE): «5 марта
     * повтор отменён». Дата-вхождение из набора не показывается, даже
     * якорная. Чужие события; своим отмены прикладывает сервер.
     */
    val exdates: Set<LocalDate> = emptySet(),
    /** Скрывать вхождения до сегодняшней даты («удалять прошедшие»). */
    val hidePastOccurrences: Boolean = false,

    /**
     * Время начала события — минуты от полуночи (0..1439). null — у события
     * нет времени: это дневная метка (как было до появления времени).
     * Время задают обычные и повторяющиеся события; день рождения и
     * «Выходной» остаются дневными метками.
     */
    val startMinutes: Int? = null,

    /**
     * Время завершения события — минуты от полуночи. null — конец не задан
     * (событие без длительности или только с началом).
     */
    val endMinutes: Int? = null,

    /**
     * Дата конца события ("yyyy-MM-dd"); null — событие в один день (конец
     * в тот же день, что и [date]).
     *
     * Свои события всегда в одних сутках: конец раньше начала недопустим
     * (см. редактор события). Чужие (Google, Яндекс) могут длиться дольше
     * суток — «1 марта 10:00 — 5 марта 18:00»; их конец хранится здесь, а
     * в календаре такое событие занимает каждый день своей длительности
     * (см. [EventExpander]).
     */
    val endDate: String? = null,

    val position: Int = 0,

    /**
     * Аккаунт-источник при CalDAV-подписке (null — локальное событие).
     * События с аккаунтом показываются как есть с сервера и только
     * для чтения: правятся на сервере, а не в редакторе приложения.
     * Приписку подписке (и отправку в неё) получает и локальное событие —
     * так что по accountId происхождение не видно.
     */
    val caldavAccountId: Long? = null,

    /**
     * UID появился в базе вычиткой с сервера — событие импортировано.
     * Именно этот флаг рисует бейдж источника «Яндекс» в редакторе дня:
     * свои события меняют флаг из ложного и после отправки в подписку,
     * правка и перепись его не трогают.
     */
    val remoteImported: Boolean = false,

    /**
     * Момент создания (epoch millis). Для серверных событий — CREATED,
     * а без него DTSTAMP. null — унаследовано от старых версий.
     * Основа будущего разрешения конфликтов (последняя правка побеждает).
     */
    val createdAt: Long? = null,
    /**
     * Момент последней правки (epoch millis): для серверных —
     * LAST-MODIFIED, а без него DTSTAMP; для локальных правок — момент
     * сохранения. null — унаследовано от старых версий.
     */
    val modifiedAt: Long? = null,
) {
    fun dateOrNull(): LocalDate? = date.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** [repeatUntil] как дата или null (бессрочно / неразборчиво). */
    fun untilOrNull(): LocalDate? = repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** [endDate] как дата или null (событие в один день / неразборчиво). */
    fun endDateOrNull(): LocalDate? = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Порядок начала и конца корректен: конец не раньше начала.
     *
     * Сравниваются моменты, а не минуты: конец на следующие сутки (у
     * чужих событий Google и Яндекса) — не ошибка, а нормальная длительность.
     * В одних сутках конец раньше начала — ошибка ввода, и редактор её не
     * пропускает (см. ScheduledEventEditorDialog).
     */
    fun hasValidTimeRange(): Boolean {
        val start = startMinutes ?: return true
        val end = endMinutes ?: return true
        val startDay = dateOrNull() ?: return end >= start
        val endDay = endDateOrNull() ?: startDay
        // Конец позже начала по дням — порядок задан датами.
        if (endDay.isAfter(startDay)) return true
        return end >= start
    }

    /**
     * Границы события по суткам: с какого по какое время оно идёт в день
     * [day].
     *
     * Событие длиннее суток (у чужих — Google, Яндекс) в каждый день
     * показывается своей частью: первый день — до полуночи, последний — от
     * полуночи до своего конца, средние — целиком. null — событие в этот
     * день не идёт.
     */
    fun windowOn(day: LocalDate): Pair<Int?, Int?>? {
        val start = dateOrNull() ?: return null
        val end = endDateOrNull() ?: start
        if (day.isBefore(start) || day.isAfter(end)) return null
        // Событие в одни сутки: его часы и есть окно дня.
        if (end == start) return startMinutes to endMinutes
        val from = if (day == start) startMinutes else 0
        val until = if (day == end) endMinutes else MINUTES_PER_DAY
        return from to until
    }

    /**
     * Происходит ли повторяющееся событие в дату [date]
     * (якорное первое вхождение тоже считается).
     *
     * Семантика RFC 5545: якорь (DTSTART) — всегда вхождение, даже если
     * выбранные дни недели его не включают; остальные вхождения — по
     * шаблону частоты с выравниванием от якоря.
     */
    fun occursOn(date: LocalDate): Boolean {
        if (type != ScheduledEventType.REPEATING) return false
        val anchor = dateOrNull() ?: return false
        if (date.isBefore(anchor)) return false
        val unit = repeatUnit ?: return false
        val interval = repeatInterval.takeIf { it > 0 } ?: return false
        // EXDATE вычёркивает вхождение из набора целиком (RFC 5545),
        // включая якорное: «5 марта отменено» — 5 марта пусто.
        if (date in exdates) return false
        if (date == anchor) return true
        val until = untilOrNull()
        if (until != null && date.isAfter(until)) return false
        return when (unit) {
            RepeatUnit.DAYS -> ChronoUnit.DAYS.between(anchor, date) % interval == 0L

            RepeatUnit.WEEKS -> {
                // Без выбранных дней повтор — по дню недели якоря.
                val selected = if (repeatWeekdays.isNotEmpty()) repeatWeekdays else setOf(anchor.dayOfWeek)
                if (date.dayOfWeek !in selected) return false
                // Выравнивание по календарным неделям с понедельника
                // (WKST=MO — умолчание RFC 5545, так считает и Яндекс).
                val weeks = ChronoUnit.WEEKS.between(anchor.mondayOfWeek(), date.mondayOfWeek())
                weeks % interval == 0L
            }

            RepeatUnit.MONTHS -> {
                val months = ChronoUnit.MONTHS.between(anchor, date)
                months % interval == 0L && date.dayOfMonth == anchor.dayOfMonth
            }

            RepeatUnit.YEARS -> {
                val years = ChronoUnit.YEARS.between(anchor, date)
                years % interval == 0L &&
                    date.month == anchor.month &&
                    date.dayOfMonth == anchor.dayOfMonth
            }
        }
    }

    /**
     * Закончились ли у события вхождения к дате [today]: оно больше
     * нигде не появится, и «Удалить прошедшие» может его убрать.
     *
     * Кандидат — якорь в прошлом и без будущего: регулярный день (без
     * повтора), повторяющийся с датой-ограничителем раньше [today].
     * Бессрочные повторения и дни рождения не заканчиваются никогда —
     * они не кандидат. Битое повторение (без частоты) тоже остаётся:
     * правило не удаляет то, чего не поняло.
     *
     * У события длиннее суток «прошло» определяется последним днём
     * ([endDate]), а не якорем: «1 марта — 5 марта» живёт до конца 5-го.
     */
    fun isOverBefore(today: LocalDate): Boolean {
        val anchor = dateOrNull() ?: return false
        val last = endDateOrNull() ?: anchor
        if (!last.isBefore(today)) return false
        return when (type) {
            ScheduledEventType.REGULAR, ScheduledEventType.WEEKEND -> true
            ScheduledEventType.BIRTHDAY -> false
            ScheduledEventType.REPEATING -> untilOrNull()?.isBefore(today) == true
        }
    }

    /** Понедельник календарной недели, содержащей дату. */
    private fun LocalDate.mondayOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Вычеркнута ли дата [date] отменами (EXDATE). Для событий без повторов
     * — «якорный день пришёлся на отмену»: вхождение не показывается.
     * occursOn уже учитывает набор, это проверка для якорных событий,
     * которые в разворот идут по точной дате, а не по occursOn.
     */
    fun isExcludedOn(date: LocalDate): Boolean = date in exdates

    companion object {

        /** Цвет не выбран: иконка красится тематическим цветом приложения. */
        const val DEFAULT_COLOR = 0L

        /** Минут в сутках — граница окна дня для много-дневных событий. */
        const val MINUTES_PER_DAY = 24 * 60

        /** RFC-коды дней недели (BYDAY) — формат хранения в БД и ICS. */
        private val weekdayCodes: Map<DayOfWeek, String> = linkedMapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )

        /** Набор дней недели → "MO,WE,FR"; пустой набор — null. */
        fun weekdaysToCode(weekdays: Set<DayOfWeek>): String? = if (weekdays.isEmpty()) {
            null
        } else {
            weekdayCodes.entries.filter { it.key in weekdays }.joinToString(",") { it.value }
        }

        /** "MO,WE,FR" → набор дней недели; неразборчивые коды пропускаются. */
        fun weekdaysFromCode(code: String?): Set<DayOfWeek> = code
            ?.split(',')
            ?.mapNotNull { raw -> weekdayCodes.entries.firstOrNull { it.value == raw.trim() }?.key }
            ?.toSet()
            ?: emptySet()

        /**
         * Набор дат → "2026-03-05,2026-03-12" (по возрастанию — для
         * стабильного round-trip); пустой набор — null.
         */
        fun exdatesToCode(exdates: Set<LocalDate>): String? =
            exdates.sorted().joinToString(",") { it.toString() }.takeIf { exdates.isNotEmpty() }

        /** "2026-03-05,2026-03-12" → набор дат; неразборчивые пропускаются. */
        fun exdatesFromCode(code: String?): Set<LocalDate> = code
            ?.split(',')
            ?.mapNotNull { raw ->
                raw.trim().takeIf { it.isNotEmpty() }?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            }
            ?.toSet()
            ?: emptySet()

        /** Фактическая month/day дня рождения [anchor] в году [year]. */
        fun effectiveBirthdayDate(anchor: LocalDate, year: Int): Pair<Int, Int> {
            val leap = Year.isLeap(year.toLong())
            return if (anchor.monthValue == 2 && anchor.dayOfMonth == 29 && !leap) {
                2 to 28
            } else {
                anchor.monthValue to anchor.dayOfMonth
            }
        }
    }
}
