package ru.taurlom.tnote.data.backup

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Заголовок SQLite-файла: магическая строка и PRAGMA user_version,
 * в котором Room хранит версию схемы БД.
 *
 * Нужен, чтобы проверить версию схемы в копии ДО подмены живой базы:
 * «слишком новая» база падает при первом открытии Room — уже после
 * того, как старые данные стёрты. Читаются только первые 100 байт:
 * файл базы любого размера не грузится в память.
 *
 * Публичные функции чистые, без Android — покрываются юнит-тестами
 * на JVM (см. SQLiteHeaderTest).
 */
internal object SQLiteHeader {

    private val MAGIC = "SQLite format 3\u0000".toByteArray() // 16 байт
    private const val HEADER_BYTES = 100
    private const val USER_VERSION_OFFSET = 60 // 4 байта, big-endian

    /**
     * Версия user_version из начала файла или null, если файл не
     * читается или не является SQLite (короче заголовка, битая магия).
     */
    fun fileVersion(file: File): Int? {
        val header = try {
            RandomAccessFile(file, "r").use { raf ->
                ByteArray(HEADER_BYTES).also { raf.readFully(it) }
            }
        } catch (_: IOException) {
            // В том числе EOFException: файл короче заголовка.
            return null
        }
        return headerVersion(header)
    }

    /**
     * Версия user_version из байт заголовка или null, если магическая
     * строка не совпала либо заголовок короче поля версии. Настоящий
     * SQLite-файл всегда не короче страницы (512+ байт), поэтому
     * «валидная магия, но мало байт» означает битый файл.
     */
    fun headerVersion(header: ByteArray): Int? {
        if (header.size < USER_VERSION_OFFSET + 4) return null
        if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        var value = 0
        for (i in 0 until 4) {
            value = (value shl 8) or (header[USER_VERSION_OFFSET + i].toInt() and 0xFF)
        }
        return value
    }
}
