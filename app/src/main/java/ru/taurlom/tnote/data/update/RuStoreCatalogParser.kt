package ru.taurlom.tnote.data.update

/**
 * Извлечение номера последней опубликованной версии из HTML-страницы
 * приложения в каталоге RuStore. Каталог — server-side rendered Next.js:
 * версия попадает в машиночитаемые места страницы, из которых надёжнее
 * всего блок JSON-LD (schema.org SoftwareApplication, поле
 * `softwareVersion`) — семантическая разметка с контрактной структурой.
 *
 * Регулярка терпит экранирование кавычек, чтобы находить значение и в
 * обычном JSON, и в потоковых данных React (`\"softwareVersion\"`).
 * При смене формата разметки парсер вернёт null — проверка тихо станет
 * «недоступной», без ложных диалогов «обновитесь».
 */
internal object RuStoreCatalogParser {

    /** `softwareVersion:"1.25.1"` и экранированный вариант `softwareVersion:\"1.25.1\"`. */
    private val jsonLdVersion = Regex("softwareVersion\\\\?\"\\s*:\\s*\\\\?\"(\\d+(?:\\.\\d+)+)\\\\?\"")

    /** Запасной вариант: микроразметка `itemProp="softwareVersion">1.25.1<`. */
    private val microdataVersion = Regex("itemProp=\"softwareVersion\"[^>]*>\\s*(\\d+(?:\\.\\d+)+)\\s*<")

    /** @return номер версии (например, «1.25.1») или null, если не найден. */
    fun parseLatestVersionName(html: String): String? = jsonLdVersion.find(html)?.groupValues?.get(1)
        ?: microdataVersion.find(html)?.groupValues?.get(1)
}
