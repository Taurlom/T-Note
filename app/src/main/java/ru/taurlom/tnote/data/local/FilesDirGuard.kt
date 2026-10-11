package ru.taurlom.tnote.data.local

import android.content.Context
import java.io.File

/**
 * Удаление файла по относительному пути из БД. Путь — недоверенный:
 * база могла быть восстановлена из чужого архива (импорт подменяет её
 * целиком, не проверяя строки photoPaths), и запись вида
 * «../databases/time_manager.db» не должна стирать файлы вне filesDir.
 * Канонизируем оба пути и удаляем только то, что осталось внутри
 * filesDir; всё остальное молча игнорируется — удаление вложений и так
 * «лучшее усилие», падать здесь незачем.
 */
internal fun Context.deleteFileInsideFilesDir(relativePath: String) {
    val file = fileInsideFilesDir(relativePath) ?: return
    file.delete()
}

/**
 * Чтение файла по относительному пути из БД — с той же паранойей, что и
 * удаление: «../databases/…» не должно утечь в шэринг заметки. Нет файла
 * или путь вне filesDir — null.
 */
internal fun Context.readFileInsideFilesDir(relativePath: String): ByteArray? {
    val file = fileInsideFilesDir(relativePath) ?: return null
    return runCatching { file.readBytes() }.getOrNull()
}

/** Канонизированный файл, гарантированно внутри filesDir, иначе null. */
private fun Context.fileInsideFilesDir(relativePath: String): File? {
    val root = filesDir.canonicalFile
    val file = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return null
    return if (file.path.startsWith(root.path + File.separator)) file else null
}
