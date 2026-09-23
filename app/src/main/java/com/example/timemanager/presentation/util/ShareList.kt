package com.example.timemanager.presentation.util

import android.content.Context
import android.content.Intent
import com.example.timemanager.R
import com.example.timemanager.domain.model.Task

/**
 * Поделиться списком текстом: заголовок + пункты с «чекбоксами».
 * Получателю не нужен T-Note — текст читается в любом мессенджере,
 * почте, SMS.
 */
fun shareList(context: Context, categoryName: String, tasks: List<Task>) {
    val body = buildListShareText(categoryName, tasks)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_list))
    )
}

/**
 * Текст списка для отправки:
 *
 * ```
 * Продукты
 *
 * ✅ Молоко
 * ⬜ Хлеб — цельнозерновой
 * ```
 */
fun buildListShareText(categoryName: String, tasks: List<Task>): String =
    buildString {
        append(categoryName.trim())
        if (tasks.isNotEmpty()) {
            append("\n\n")
            tasks.forEach { task ->
                append(if (task.isCompleted) "✅ " else "⬜ ")
                append(task.title.trim())
                if (task.description.isNotBlank()) {
                    append(" — ")
                    append(task.description.trim())
                }
                append('\n')
            }
            // Финальный перевод строки от buildString не нужен.
            setLength(length - 1)
        }
    }.trim()
