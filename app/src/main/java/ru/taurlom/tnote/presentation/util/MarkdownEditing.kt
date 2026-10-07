package ru.taurlom.tnote.presentation.util

import androidx.compose.ui.text.TextRange

/**
 * Операции панели форматирования над сырым markdown-текстом поля ввода.
 * Каждая возвращает новый текст и новую позицию выделения; чистые функции —
 * тестируются без Compose.
 */
object MarkdownEditing {

    /** Оборачивает выделение в `**…**`; уже жирное — снимает. */
    fun toggleBold(text: String, selection: TextRange): Pair<String, TextRange> {
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        // Уже обёрнуто?
        if (start >= 2 && end + 2 <= text.length &&
            text.startsWith("**", start - 2) && text.startsWith("**", end)
        ) {
            val inner = text.substring(start, end)
            return (text.take(start - 2) + inner + text.substring(end + 2)) to
                TextRange(start - 2, end - 2)
        }
        val selected = text.substring(start, end)
        val result = text.take(start) + "**" + selected + "**" + text.substring(end)
        // Выделение остаётся на тексте внутри меток.
        return result to TextRange(start + 2, end + 2)
    }

    /** Добавляет/снимает префикс строки (`# `, `## `, `- `) в позиции курсора. */
    fun toggleLinePrefix(text: String, selection: TextRange, prefix: String): Pair<String, TextRange> {
        val caret = selection.end.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let {
            if (it < 0 || caret == 0) 0 else it + 1
        }
        if (text.startsWith(prefix, lineStart)) {
            val result = text.take(lineStart) + text.substring(lineStart + prefix.length)
            return result to TextRange((caret - prefix.length).coerceAtLeast(lineStart))
        }
        val result = text.take(lineStart) + prefix + text.substring(lineStart)
        return result to TextRange(caret + prefix.length)
    }

    /**
     * Выделение превращает в `[выбранное](url)`; курсор встаёт на `url`.
     * [placeholder] — подпись ссылки, если выделение пусто (локализуемый
     * ресурс, передаёт экран).
     */
    fun insertLink(
        text: String,
        selection: TextRange,
        placeholder: String
    ): Pair<String, TextRange> {
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        val selected = text.substring(start, end)
        val label = selected.ifEmpty { placeholder }
        val result = text.take(start) + "[$label]()" + text.substring(end)
        val urlStart = start + label.length + 3
        return result to TextRange(urlStart, urlStart)
    }
}
