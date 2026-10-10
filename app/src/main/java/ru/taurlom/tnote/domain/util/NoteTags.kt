package ru.taurlom.tnote.domain.util

import ru.taurlom.tnote.domain.model.Note

/**
 * Теги заметки: свободные слова-метки («рецепт», «кулинария»), которые
 * в просмотре показываются бейджами и позволяют искать заметки по `#тегу`.
 *
 * Хранение — один массив строк в [Note.tags]; в Entity — одна колонка
 * tags, значения через перевод строки (сам тег перевода строки содержать
 * не может). Ввод в редакторе — строка «через запятую».
 */
object NoteTags {

    /** Потолок на длину тега: защищает бейджи от «фраз» вместо меток. */
    const val MAX_TAG_LENGTH = 50

    /** Потолок на число тегов заметки. */
    const val MAX_TAGS = 20

    /** Разделитель в колонке базы. */
    private const val STORAGE_SEPARATOR = '\n'

    /** Разделители в поле ввода: запятая, точка с запятой, пробелы, переносы. */
    private val INPUT_DELIMITERS = Regex("[,;\\s]+")

    /**
     * Список из строки редактора: режется по разделителям, каждый тег
     * [normalize]уется. Возвращает ровно то, что увидят бейди — строка
     * ввода при сохранении пересобирается из полученного списка
     * (см. [toInput]), поэтому «лишние» пробелы и дубли не накапливаются.
     */
    fun fromInput(input: String): List<String> =
        normalize(input.split(INPUT_DELIMITERS))

    /** Строка для поля ввода редактора: теги через запятую. */
    fun toInput(tags: List<String>): String = tags.joinToString(", ")

    /**
     * Нормализация: обрезка пробелов, отброс пустых и дублей без учёта
     * регистра (с сохранением первого написания — человек так и вводил),
     * ограничение длины и числа. Список сохраняется в порядке ввода.
     */
    fun normalize(tags: List<String>): List<String> {
        val seen = HashSet<String>()
        return tags.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }
            .filter { seen.add(it.lowercase()) }
            .take(MAX_TAGS)
            .toList()
    }

    /** Колонка Entity: теги через перевод строки. */
    fun toStored(tags: List<String>): String =
        normalize(tags).joinToString(STORAGE_SEPARATOR.toString())

    /** Список из колонки Entity. */
    fun parseStored(stored: String): List<String> =
        normalize(stored.split(STORAGE_SEPARATOR))

    /**
     * Подходит ли заметка под поисковый запрос. Запрос с `#` — поиск
     * строго по целому тегу (регистр не важен, «#рецепт» не найдёт
     * «#рецепты»); `#` без текста и обычный запрос — как substring-поиск
     * по заголовку и тексту (см. searchMatch). Пустой запрос матчит всё.
     */
    fun matches(note: Note, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (q.startsWith("#")) {
            val tag = q.substring(1).trim()
            // «#» без тега — человек ещё печатает: не фильтруем, как пустой.
            if (tag.isEmpty()) return true
            return note.tags.any { it.equals(tag, ignoreCase = true) }
        }
        return note.title.contains(q, ignoreCase = true) ||
            note.content.contains(q, ignoreCase = true)
    }
}
