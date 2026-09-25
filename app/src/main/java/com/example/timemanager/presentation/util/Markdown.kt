package com.example.timemanager.presentation.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Рендер markdown-подмножества заметок в [AnnotatedString].
 *
 * Поддерживается: `#`/`##`/`###` заголовки, `**жирный**`, списки `-`/`*` и
 * `1.`, ссылки `[текст](url)`. Всё остальное — обычный текст: исходник
 * остаётся читаемым в любой версии приложения и при пересылке, а в базе
 * лежит plain text вместо хрупких span-метаданных.
 */
@OptIn(ExperimentalTextApi::class)
object Markdown {

    private val headingStyles = mapOf(
        1 to SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
        2 to SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        3 to SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
    )

    fun render(source: String): AnnotatedString = AnnotatedString.Builder().apply {
        source.split('\n').forEachIndexed { index, line ->
            if (index > 0) append('\n')
            appendLine(line)
        }
    }.toAnnotatedString()

    /**
     * Плоский текст для превью в списке: метки markdown убраны, строки
     * склеены, результат обрезан по границе слова.
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
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""\[([^\]]*)\]\([^)]*\)"""), "$1")
        if (flat.length <= maxLength) return flat
        val cut = flat.take(maxLength + 1)
        val lastSpace = cut.lastIndexOf(' ')
        return flat.take(if (lastSpace > 0) lastSpace else maxLength).trimEnd() + "…"
    }

    // ---------- internals ----------

    private fun AnnotatedString.Builder.appendLine(line: String) {
        val level = headingMarker(line)
        if (level != null) {
            val style = headingStyles[level] ?: headingStyles[3]!!
            withStyle(style) { appendInline(line.drop(level + 1).trim()) }
            return
        }
        when {
            Regex("""^[-*]\s+""").containsMatchIn(line) -> {
                append("• ")
                appendInline(line.replace(Regex("""^[-*]\s+"""), ""))
            }
            else -> appendInline(line)
        }
    }

    /** Уровень заголовка строки (1..3) или null, если строка не заголовок. */
    private fun headingMarker(line: String): Int? {
        val trimmed = line.trimStart()
        val hashes = trimmed.takeWhile { it == '#' }.count()
        return if (hashes in 1..3 && trimmed.drop(hashes).startsWith(' ')) hashes else null
    }

    /** Инлайн-разбор: `**жирный**` и `[текст](url)`; остальное — как есть. */
    private fun AnnotatedString.Builder.appendInline(raw: String) {
        var i = 0
        while (i < raw.length) {
            if (raw.startsWith("**", i)) {
                val end = raw.indexOf("**", i + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if (raw[i] == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                val closeParen =
                    if (closeBracket >= 0 && raw.startsWith("](", closeBracket)) {
                        raw.indexOf(')', closeBracket + 2)
                    } else {
                        -1
                    }
                if (closeParen >= 0) {
                    val label = raw.substring(i + 1, closeBracket)
                    val url = raw.substring(closeBracket + 2, closeParen)
                    if (label.isNotEmpty() && url.isNotBlank()) {
                        // Цвет и клик по ссылке обеспечивает Text
                        // (LinkAnnotation.Url + LocalUriHandler), здесь только
                        // подчёркивание для наглядности.
                        val start = length
                        append(label)
                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, length)
                        addLink(LinkAnnotation.Url(url), start, length)
                        i = closeParen + 1
                        continue
                    }
                }
            }
            append(raw[i])
            i++
        }
    }
}
