package ru.taurlom.tnote.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuStoreCatalogParserTest {

    /** Страница каталога: блок JSON-LD идёт раньше потоковых данных React. */
    private val realPageShape = """
        <!DOCTYPE html><html><head>
        <script type="application/ld+json">{"@context":"https://schema.org","@type":"SoftwareApplication",
        "name":"T-Note","operatingSystem":"Android","softwareVersion":"1.25.1",
        "fileSize":"7323791","dateModified":"2026-11-08T10:07:08.008Z"}</script>
        </head><body>
        <p class="_sWFKJYb">Версия <span itemProp="softwareVersion">1.25.1</span></p>
        </body></html>
    """.trimIndent()

    @Test
    fun `версия извлекается из json-ld`() {
        assertEquals("1.25.1", RuStoreCatalogParser.parseLatestVersionName(realPageShape))
    }

    @Test
    fun `экранированный вариант в данных react тоже читается`() {
        // Фрагмент реальной страницы: flight-данные React хранят кавычки
        // экранированными («softwareVersion\":\"1.27.0\"»).
        val html = """
            <html><body><script>
            self.__next_f.push([1,"{\"versionName\":\"1.27.0\",\"appStatus\":\"PUBLISHED\"}"])
            </script></body></html>
        """.trimIndent()
            .replace("versionName", "softwareVersion")
        assertEquals("1.27.0", RuStoreCatalogParser.parseLatestVersionName(html))
    }

    @Test
    fun `запасной вариант — микроразметка`() {
        val html = """
            <html><body>
            <p>Версия <span itemProp="softwareVersion"> 1.26.8 </span></p>
            </body></html>
        """.trimIndent()
        assertEquals("1.26.8", RuStoreCatalogParser.parseLatestVersionName(html))
    }

    @Test
    fun `короткий номер без патча читается, сравнение доделает нули`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@type":"SoftwareApplication","softwareVersion":"1.25"}</script></head></html>
        """.trimIndent()
        // Каталог публикует MAJOR.MINOR.PATCH, но и «1.25» не сюрприз:
        // сравнение версий считает её равной 1.25.0.
        assertEquals("1.25", RuStoreCatalogParser.parseLatestVersionName(html))
    }

    @Test
    fun `пустая страница и чужая разметка дают null`() {
        assertNull(RuStoreCatalogParser.parseLatestVersionName(""))
        assertNull(
            RuStoreCatalogParser.parseLatestVersionName(
                "<html><head><script type=\"application/ld+json\">" +
                    "{\"@type\":\"SoftwareApplication\"}</script></head></html>",
            ),
        )
    }

    @Test
    fun `берётся первое совпадение — из json-ld, а не из хвоста страницы`() {
        val html = realPageShape
            .replace("1.25.1", "1.30.0")
            .replace(
                Regex("itemProp=\"softwareVersion\">[^<]*<"),
                "itemProp=\"softwareVersion\">9.9.9<",
            )
        assertEquals("1.30.0", RuStoreCatalogParser.parseLatestVersionName(html))
    }
}
