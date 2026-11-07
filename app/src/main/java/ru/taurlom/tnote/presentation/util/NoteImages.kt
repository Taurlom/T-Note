package ru.taurlom.tnote.presentation.util

import android.content.Context
import androidx.core.net.toUri
import java.io.File

/**
 * Модель Coil для ссылки из текста заметки: `content://` как есть (картинки,
 * пришедшие «Поделиться»), остальное — файл из filesDir по относительному
 * пути (`note_photos/…`). Общий для редактора, просмотра и миниатюр списка —
 * все три места обязаны показывать один и тот же файл.
 */
fun noteImageModel(context: Context, ref: String): Any = if (ref.startsWith("content://")) ref.toUri() else File(context.filesDir, ref)
