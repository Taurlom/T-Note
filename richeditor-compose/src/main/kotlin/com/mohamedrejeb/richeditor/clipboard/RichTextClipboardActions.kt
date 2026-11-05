@file:OptIn(com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi::class)

package com.mohamedrejeb.richeditor.clipboard

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Копирует текущее выделение в системный буфер обмена как rich text (HTML) +
 * plain text — тот же payload, что системное меню копирования строит через
 * [AndroidRichTextClipboardManager.setClipEntry]. Возвращает false, если
 * выделение пустое.
 *
 * Функция живёт вне CompositionLocalProvider редактора, поэтому принимает
 * платформенный [Clipboard] напрямую и не полагается на rich-обёртку.
 */
public suspend fun RichTextState.copySelectedText(clipboard: Clipboard): Boolean {
    val selection = copySelection ?: return false
    if (selection.collapsed) return false

    return try {
        // С rich-буфером — HTML + plain text; без него — plain text через
        // toText (как в AndroidRichTextClipboardManager.setClipEntry).
        val clipData = if (config.richClipboardEnabled) {
            ClipData.newHtmlText(
                "rich text",
                toText(selection),
                toHtml(selection),
            )
        } else {
            ClipData.newPlainText("text", toText(selection))
        }
        clipboard.setClipEntry(ClipEntry(clipData))
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Вставляет содержимое системного буфера обмена в текущую позицию курсора
 * (или вместо выделения): HTML из буфера (скопированный из rich-редактора)
 * применяется с форматированием, иначе вставляется plain text — тот же путь,
 * что системное меню вставки. Возвращает false, если в буфере нет текста.
 *
 * Системное меню делает это в два шага: getClipEntry сташит HTML в
 * [RichTextState.pendingClipboardHtml], затем вставка текста в поле
 * распознаётся как paste и HTML применяется. Здесь оба шага делаются явно:
 * кнопка живёт вне CompositionLocalProvider редактора, поэтому LocalClipboard
 * возвращает платформенный [Clipboard] без rich-обёртки.
 */
public suspend fun RichTextState.pasteFromClipboard(clipboard: Clipboard): Boolean {
    val entry = try {
        clipboard.getClipEntry()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } ?: return false

    val clipData = entry.clipData
    if (clipData.itemCount == 0) return false
    val item = clipData.getItemAt(0)
    val text = item.text?.toString() ?: return false

    if (config.richClipboardEnabled) {
        item.htmlText?.let { html ->
            pendingClipboardHtml = html
            pendingClipboardPlainText = text
        }
    }

    val oldValue = textFieldValue
    val start = oldValue.selection.min
    val end = oldValue.selection.max
    // Замена выделения текстом нужна только для распознавания paste:
    // isPaste-ветка onTextFieldValueChange сама снимает выделение и
    // вставляет HTML по позиции курсора.
    val newText = oldValue.text.replaceRange(start, end, text)
    onTextFieldValueChange(
        TextFieldValue(
            text = newText,
            selection = TextRange(start + text.length),
        ),
    )
    return true
}
