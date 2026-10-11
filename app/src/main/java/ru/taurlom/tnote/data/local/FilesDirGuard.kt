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
    val root = filesDir.canonicalFile
    val file = runCatching { File(filesDir, relativePath).canonicalFile }.getOrNull() ?: return
    if (file.path.startsWith(root.path + File.separator)) {
        file.delete()
    }
}
