package ru.taurlom.tnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionsTest {

    @Test
    fun `равные версии равны`() {
        assertEquals(0, compareVersionNames("1.25.1", "1.25.1"))
    }

    @Test
    fun `мажорная разница важнее минорной`() {
        assertEquals(1, compareVersionNames("2.0.0", "1.99.99"))
        assertEquals(-1, compareVersionNames("1.0.0", "2.0.0"))
    }

    @Test
    fun `компоненты сравниваются числами, а не строками`() {
        // Строковое сравнение сказало бы «10» < «9».
        assertEquals(1, compareVersionNames("1.10.0", "1.9.9"))
        assertEquals(-1, compareVersionNames("1.9.9", "1.10.0"))
    }

    @Test
    fun `разное число компонентов — короткое дополняется нулями`() {
        assertEquals(0, compareVersionNames("1.25", "1.25.0"))
        assertEquals(1, compareVersionNames("1.25.1", "1.25"))
    }

    @Test
    fun `патчовая разница определяется`() {
        assertEquals(1, compareVersionNames("1.26.8", "1.26.7"))
        assertEquals(-1, compareVersionNames("1.26.7", "1.26.8"))
    }

    @Test
    fun `не-числовой компонент считается нулём`() {
        // Защита от неожиданного ответа каталога, а не поддержка суффиксов.
        assertEquals(0, compareVersionNames("1.25.1", "1.25.1.abc"))
        assertEquals(1, compareVersionNames("1.25.2", "1.25.1.abc"))
    }
}
