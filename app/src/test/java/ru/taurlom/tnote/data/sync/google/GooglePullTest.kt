package ru.taurlom.tnote.data.sync.google

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.taurlom.tnote.domain.model.RepeatUnit
import ru.taurlom.tnote.domain.model.ScheduledEventType
import java.time.LocalDate

/**
 * Стадия вычитки Google из накопленных items ([googlePullFromItems]):
 * исключения серий учитываются до мастеров независимо от порядка в списке
 * (мастер и вхождение могут попасть на разные страницы), дни отменённых и
 * перенесённых вхождений примешиваются к exdates мастера, наследные строки
 * вхождений защищены UID'ами, а нечитаемые items делают снапшот неполным.
 */
class GooglePullTest {

    private fun json(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    private fun day(value: String): JSONObject = JSONObject().put("date", value)

    /** Мастер серии в нашей канонической форме: «tnote» + UUID без дефисов. */
    private val masterId = "tnote11111111222233334444555555555555"
    private val masterUid = "11111111-2222-3333-4444-555555555555"

    private fun master(summary: String = "Зарядка"): JSONObject = json(
        "id" to masterId,
        "status" to "confirmed",
        "summary" to summary,
        "start" to day("2026-11-17"),
        "end" to day("2026-11-18"),
        "recurrence" to JSONArray().put("RRULE:FREQ=DAILY"),
    )

    /** Вхождение серии: отменённое (по умолчанию) или перенесённое. */
    private fun instance(
        id: String = "${masterId}_R20261119",
        status: String = "cancelled",
        originalDay: String? = "2026-11-19",
        startDay: String = "2026-11-19",
    ): JSONObject = json(
        "id" to id,
        "recurringEventId" to masterId,
        "status" to status,
        "originalStartTime" to originalDay?.let(::day),
        "start" to day(startDay),
        "end" to day(startDay),
    )

    @Test
    fun `cancelled instance freezes the master and suppresses its day`() {
        // Вхождение стоит в списке раньше мастера — порядок не важен:
        // исключения учитываются первым проходом по всей накопленной вычитке.
        val pull = googlePullFromItems("cal", listOf(instance(), master()))

        val remote = pull.events.single()
        assertEquals(masterUid, remote.event.uid)
        // Отменённое вхождение не разворачивается мастером как живое.
        assertEquals(setOf(LocalDate.parse("2026-11-19")), remote.event.exdates)
        // PUT по мастеру перечеркнул бы исключение — серия заморожена.
        assertTrue(remote.notReproducible)
        // Наследная строка вхождения не должна уехать обратно на сервер.
        assertTrue("${masterId}_R20261119" in pull.protectedUids)
        assertEquals(0, pull.unreadable)
        assertTrue(pull.complete)
    }

    @Test
    fun `moved instance suppresses the original day`() {
        // Перенос: исходный слот 20-го, новое начало 21-го. Мастер больше не
        // разворачивает 20-е (а новое время вхождения моделью невыразимо —
        // это остаётся за полным решением с хранением исходного JSON).
        val moved = instance(
            id = "${masterId}_R20261120",
            status = "confirmed",
            originalDay = "2026-11-20",
            startDay = "2026-11-21",
        )
        val pull = googlePullFromItems("cal", listOf(master(), moved))

        val remote = pull.events.single()
        assertEquals(setOf(LocalDate.parse("2026-11-20")), remote.event.exdates)
        assertTrue(remote.notReproducible)
        // Само вхождение в базу не попало — отдельной строкой оно было бы
        // дублем развёртки мастера.
        assertEquals(listOf(masterUid), pull.events.map { it.event.uid })
    }

    @Test
    fun `cancelled master is skipped without spoiling completeness`() {
        val cancelled = json("id" to "other", "status" to "cancelled", "start" to day("2026-11-17"))
        val pull = googlePullFromItems("cal", listOf(cancelled, master()))

        // Отменённый мастер — «удалено» для diff'а движка: в списке его нет,
        // а снапшот полон.
        assertEquals(listOf(masterUid), pull.events.map { it.event.uid })
        assertEquals(0, pull.unreadable)
        assertTrue(pull.complete)
    }

    @Test
    fun `unreadable items make the snapshot incomplete`() {
        val noStart = json("id" to "broken", "summary" to "Без начала")
        val pull = googlePullFromItems("cal", listOf(null, noStart, master()))

        assertEquals(2, pull.unreadable)
        assertFalse(pull.complete)
        assertEquals(listOf(masterUid), pull.events.map { it.event.uid })
    }

    @Test
    fun `human description freezes the event`() {
        val event = json(
            "id" to "x",
            "status" to "confirmed",
            "summary" to "Встреча",
            "description" to "Обсудим смету",
            "start" to day("2026-11-17"),
            "end" to day("2026-11-18"),
        )
        val pull = googlePullFromItems("cal", listOf(event))

        val remote = pull.events.single()
        assertTrue(remote.hasDescription)
        // PUT перезаписал бы description маркером [T-Note] — объект заморожен.
        assertTrue(remote.notReproducible)
    }

    @Test
    fun `own marker description stays writable`() {
        val own = json(
            "id" to "x",
            "status" to "confirmed",
            "summary" to "Своё",
            "description" to "[T-Note] icon=NOTE",
            "start" to day("2026-11-17"),
            "end" to day("2026-11-18"),
        )
        val pull = googlePullFromItems("cal", listOf(own))

        // Служебный маркер — не человеческий текст: собственный экспорт
        // остаётся записываемым.
        assertFalse(pull.events.single().notReproducible)
    }

    @Test
    fun `note carrier keeps its description and stays writable`() {
        val note = json(
            "id" to "note-id",
            "status" to "confirmed",
            "summary" to "✍️ Заметка",
            "description" to "Текст заметки дня",
            "start" to day("2026-11-17"),
            "end" to day("2026-11-18"),
        )
        val pull = googlePullFromItems("cal", listOf(note))

        assertEquals(0, pull.unreadable)
        assertTrue(pull.events.isEmpty())
        val remote = pull.notes.single()
        assertEquals("Текст заметки дня", remote.noteText)
        assertTrue(remote.hasDescription)
        // Description носителя — payload самого объекта (текст заметки),
        // экспорт воспроизводит его дословно: заморозка не нужна.
        assertFalse(remote.notReproducible)
    }

    @Test
    fun `own event with note-like title stays an event`() {
        // ✍ в имени своего события — пользовательский текст: явный type= в
        // маркере объявляет тип, имя тип не переключает (баг 16). Без шлюза
        // «✍ Курс каллиграфии» уезжала бы из событий в заметки.
        val own = json(
            "id" to "x",
            "status" to "confirmed",
            "summary" to "✍ Курс каллиграфии",
            "description" to "[T-Note] icon=NOTE type=REGULAR",
            "start" to day("2026-11-17"),
            "end" to day("2026-11-18"),
        )
        val pull = googlePullFromItems("cal", listOf(own))

        assertTrue(pull.notes.isEmpty())
        val remote = pull.events.single()
        assertEquals(ScheduledEventType.REGULAR, remote.event.type)
        assertEquals("✍ Курс каллиграфии", remote.event.title)
        // Маркер — свои метаданные: объект записываем.
        assertFalse(remote.notReproducible)
    }

    @Test
    fun `own event with weekend-like title keeps its type`() {
        // ☀️-имя при явном type=REGULAR не конвертируется в WEEKEND:
        // событие целиком на весь день без правила выглядело бы для
        // эвристики точно как наша метка «Выходной».
        val own = json(
            "id" to "x",
            "status" to "confirmed",
            "summary" to "☀️ Отдых",
            "description" to "[T-Note] icon=NOTE type=REGULAR",
            "start" to day("2026-11-17"),
            "end" to day("2026-11-18"),
        )
        val pull = googlePullFromItems("cal", listOf(own))

        val remote = pull.events.single()
        assertEquals(ScheduledEventType.REGULAR, remote.event.type)
        assertEquals("☀️ Отдых", remote.event.title)
    }

    @Test
    fun `own yearly with cake title stays repeating`() {
        // Годовая серия своего события с 🎂 в начале имени: явный type=
        // REPEATING объявляет серию повторением, а не днём рождения —
        // без метки repeatUnit обнулился бы в BIRTHDAY.
        val own = json(
            "id" to "x",
            "status" to "confirmed",
            "summary" to "🎂 Маша",
            "description" to "[T-Note] icon=NOTE type=REPEATING",
            "start" to day("2026-01-01"),
            "end" to day("2026-01-02"),
            "recurrence" to JSONArray().put("RRULE:FREQ=YEARLY"),
        )
        val pull = googlePullFromItems("cal", listOf(own))

        val remote = pull.events.single()
        assertEquals(ScheduledEventType.REPEATING, remote.event.type)
        assertEquals(RepeatUnit.YEARS, remote.event.repeatUnit)
        assertEquals("🎂 Маша", remote.event.title)
    }

    @Test
    fun `foreign google birthday template adopts as birthday`() {
        // Шаблон «День рождения» (годовой круг на весь день, имя с 🎂):
        // у чужого объекта явных меток нет — имя остаётся единственным
        // признаком, приём шаблона сохраняется (баг 16).
        val foreign = json(
            "id" to "x",
            "status" to "confirmed",
            "summary" to "🎂 Иван",
            "start" to day("1990-04-12"),
            "end" to day("1990-04-13"),
            "recurrence" to JSONArray().put("RRULE:FREQ=YEARLY;BYMONTH=4;BYMONTHDAY=12"),
        )
        val pull = googlePullFromItems("cal", listOf(foreign))

        assertTrue(pull.notes.isEmpty())
        val remote = pull.events.single()
        assertEquals(ScheduledEventType.BIRTHDAY, remote.event.type)
        assertEquals("Иван", remote.event.title)
        assertNull(remote.event.repeatUnit)
    }
}
