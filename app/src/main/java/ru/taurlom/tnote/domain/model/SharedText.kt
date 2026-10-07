package ru.taurlom.tnote.domain.model

/**
 * Текст, принятый из системного «Поделиться» (ACTION_SEND, text/plain):
 * любое приложение может отправить в T-Note выделение или ссылку.
 *
 * [subject] — необязательный заголовок от источника (EXTRA_SUBJECT):
 * браузеры, например, кладут туда название страницы. [text] — сам текст
 * (EXTRA_TEXT). Аналог [SharedList] для файлового приёма списков.
 */
data class SharedText(val subject: String?, val text: String) {

    /**
     * Заголовок черновика заметки: subject, если источник его дал, иначе
     * первая непустая строка текста. Обрезка до [TITLE_MAX_CHARS]: список
     * заметок показывает заголовок одной строкой, а первой строкой может
     * оказаться ссылка на килобайт или весь текст без переводов строк.
     *
     * Пустой результат возможен только для пустого [text] — вход
     * (MainActivity) такие шэры отфильтровывает; заметки без заголовка
     * в списке были бы мусорной строкой.
     */
    fun draftTitle(): String {
        val fromSubject = subject?.trim().orEmpty()
        if (fromSubject.isNotEmpty()) return fromSubject.take(TITLE_MAX_CHARS)
        return text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(TITLE_MAX_CHARS)
            .orEmpty()
    }

    /**
     * Превью для диалога подтверждения: пустые строки выброшены, переводы
     * строк схлопнуты в пробел, обрезано до [maxLength] со знаком «…».
     * Чистая функция — эвристику обрезки проще протестировать, чем
     * обрезку в композиции.
     */
    fun preview(maxLength: Int = PREVIEW_MAX_CHARS): String {
        val flat = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return if (flat.length <= maxLength) flat else flat.take(maxLength).trimEnd() + "…"
    }

    private companion object {
        const val TITLE_MAX_CHARS = 80
        const val PREVIEW_MAX_CHARS = 120
    }
}
