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
     */
    fun isOverBefore(today: LocalDate): Boolean {
        val anchor = dateOrNull() ?: return false
        if (!anchor.isBefore(today)) return false
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
