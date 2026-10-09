package ru.taurlom.tnote.presentation.util

/**
 * Остатки работы с markdown-исходником заметок: плоское превью для списка
 * и белый список открываемых ссылок. Рендер и вставку меток забрал
 * WYSIWYG-редактор (compose-rich-editor): разметка в тексте не видна
 * ни при редактировании, ни при просмотре.
 */
object Markdown {

    /**
     * Плоский текст для превью в списке: метки markdown убраны, изображения
     * выкинуты, строки склеены, результат обрезан по границе слова.
     */
    fun preview(source: String, maxLength: Int = 120): String {
        val flat = source.lineSequence()
            .map { line ->
                val heading = headingMarker(line)
                (if (heading != null) line.drop(heading + 1) else line)
                    .replace(Regex("""^[-*]\s+"""), "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("""!\[[^\]]*\]\([^)\s]+\)"""), "")
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""\[([^\]]*)\]\([^)]*\)"""), "$1")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        if (flat.length <= maxLength) return flat
        val cut = flat.take(maxLength + 1)
        val lastSpace = cut.lastIndexOf(' ')
        return flat.take(if (lastSpace > 0) lastSpace else maxLength).trimEnd() + "…"
    }

    // ---------- ссылки ----------

    /** Схемы ссылок, открываемые кликом; всё остальное игнорируется. */
    private val OPENABLE_LINK_SCHEMES = setOf("http", "https", "mailto", "tel")

    /**
     * Открываема ли ссылка из заметки: схема из [OPENABLE_LINK_SCHEMES],
     * регистр не важен. Клик по ссылке уходит в системный ACTION_VIEW,
     * а заметка — потенциально недоверенный текст (копипаст, чужая
     * резервная копия): произвольная схема не должна уметь запускать
     * чужие компоненты, а схема без обработчика на устройстве — ронять
     * приложение. Ссылка без схемы не открывается по той же причине:
     * обрабатывать её некому.
     */
    fun isOpenableLink(url: String): Boolean {
        val schemeEnd = url.indexOf(':')
        if (schemeEnd < 1) return false
        return url.take(schemeEnd).lowercase() in OPENABLE_LINK_SCHEMES
    }

    // ---------- internals ----------

    /** Уровень заголовка строки (1..3) или null, если строка не заголовок. */
    private fun headingMarker(line: String): Int? {
        val trimmed = line.trimStart()
        val hashes = trimmed.takeWhile { it == '#' }.count()
        return if (hashes in 1..3 && trimmed.drop(hashes).startsWith(' ')) hashes else null
    }
}
