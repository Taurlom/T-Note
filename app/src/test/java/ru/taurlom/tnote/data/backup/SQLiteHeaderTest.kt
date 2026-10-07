package ru.taurlom.tnote.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разбор заголовка SQLite (см. https://www.sqlite.org/fileformat2.html):
 * магическая строка 16 байт, user_version — байты 60–63, big-endian.
 * Смещения продублированы здесь намеренно: тест обязан знать формат
 * независимо от реализации.
 */
class SQLiteHeaderTest {

    /** Заголовок SQLite с user_version = [version]. */
    private fun header(version: Int): ByteArray {
        val bytes = ByteArray(100)
        "SQLite format 3\u0000".toByteArray().copyInto(bytes)
        bytes[60] = (version ushr 24).toByte()
        bytes[61] = (version ushr 16).toByte()
        bytes[62] = (version ushr 8).toByte()
        bytes[63] = version.toByte()
        return bytes
    }

    private fun tempFile(): File =
        File.createTempFile("sqlite_header", ".db").apply { deleteOnExit() }

    @Test
    fun `user_version reads from header`() {
        assertEquals(16, SQLiteHeader.headerVersion(header(16)))
        assertEquals(5, SQLiteHeader.headerVersion(header(5)))
        assertEquals(0, SQLiteHeader.headerVersion(header(0)))
    }

    @Test
    fun `user_version is big-endian`() {
        // 256 = 0x00000100: значим третий байт; little-endian дал бы 65536.
        assertEquals(256, SQLiteHeader.headerVersion(header(256)))
    }

    @Test
    fun `non-SQLite bytes give null`() {
        assertNull(SQLiteHeader.headerVersion(ByteArray(100)))
        assertNull(SQLiteHeader.headerVersion("SQLite format 4\u0000".toByteArray() + ByteArray(84)))
        // Валидная магия, испорченная в первом байте.
        val broken = header(16).also { it[0] = 'x'.code.toByte() }
        assertNull(SQLiteHeader.headerVersion(broken))
    }

    @Test
    fun `header shorter than the version field gives null`() {
        // Короче 64 байт поля user_version не хватает; ровно 64 — хватает.
        assertNull(SQLiteHeader.headerVersion(header(16).copyOf(60)))
        assertEquals(16, SQLiteHeader.headerVersion(header(16).copyOf(64)))
    }

    @Test
    fun `file version reads first 100 bytes only`() {
        val file = tempFile()
        file.writeBytes(header(16) + ByteArray(4096))
        assertEquals(16, SQLiteHeader.fileVersion(file))
    }

    @Test
    fun `empty or truncated file gives null`() {
        val file = tempFile()
        assertNull(SQLiteHeader.fileVersion(file))
        file.writeBytes(ByteArray(42))
        assertNull(SQLiteHeader.fileVersion(file))
    }
}
