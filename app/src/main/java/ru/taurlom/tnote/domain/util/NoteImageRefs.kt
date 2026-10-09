package ru.taurlom.tnote.domain.util

/**
 * Ссылки на изображения внутри текста заметки.
 *
 * Картинка хранится как markdown-образ `![alt](path)` прямо в `content`,
 * поэтому позиция изображения — часть текста, а таблица `note_photos`
 * остаётся реестром файлов заметки (чистка при удалении). Порядок файлов —
 * по появлению ссылок в тексте.
 *
 * До сохранения только что выбранные фото фигурируют в тексте своими
 * content:// URI; use-case при коммите копирует файл и переписывает ссылку
 * на относительный путь через [replace].
 */
object NoteImageRefs {

    // URL — непробельная последовательность без ')' (наши относительные пути
    // и content:// URI пробелов и скобок не содержат).
    private val imageRef = Regex("""(!\[[^\]]*\]\()([^)\s]+)(\))""")

    /** Пути/URI всех изображений текста в порядке их появления. */
    fun extractAll(markdown: String): List<String> =
        imageRef.findAll(markdown).map { it.groupValues[2] }.toList()

    /** Подменяет URL ссылок по карте «старый → новый», текст и alt не трогает. */
    fun replace(markdown: String, replacements: Map<String, String>): String {
        if (replacements.isEmpty()) return markdown
        return imageRef.replace(markdown) { match ->
            val replacement = replacements[match.groupValues[2]] ?: return@replace match.value
            match.groupValues[1] + replacement + match.groupValues[3]
        }
    }

    /**
     * Текст для открытия заметки: фото, на которые в тексте ещё нет ссылок
     * (прикреплены до появления картинок в тексте), дописываются в конец
     * своими строками — после первого сохранения их позиции зафиксированы.
     */
    fun withAppendedMissing(markdown: String, photoPaths: List<String>): String {
        val referenced = extractAll(markdown).toSet()
        val missing = photoPaths.filter { it !in referenced }
        if (missing.isEmpty()) return markdown
        val appendix = missing.joinToString("\n") { "![]($it)" }
        return if (markdown.isBlank()) appendix else "$markdown\n$appendix"
    }

    /**
     * Ставит каждую ссылку изображения на отдельную строку-абзац.
     *
     * Склеенная с текстом ссылка (`…text![](path)text…`) держит картинку
     * внутри абзаца с текстом: в редакторе она делит строку с буквами, а
     * тап по такой строке позиционирует курсор по первой текстовой строке
     * абзаца — и текст вставляется не туда. Отдельная строка превращает
     * картинку в самостоятельный блок. Уже изолированные ссылки не
     * трогаются, лишних пустых строк не появляется.
     */
    fun isolateImageRefs(markdown: String): String {
        if (!imageRef.containsMatchIn(markdown)) return markdown
        return markdown
            .replace(gluedBefore) { "\n${it.value}" }
            .replace(gluedAfter) { "${it.value}\n" }
    }

    // Ссылка, склеенная с текстом слева/справа: соседний символ есть и не
    // перевод строки. Синтаксис повторяет [imageRef] без групп захвата.
    private val gluedBefore = Regex("""(?<=[^\n])!\[[^\]]*\]\([^)\s]+\)""")
    private val gluedAfter = Regex("""!\[[^\]]*\]\([^)\s]+\)(?=[^\n])""")

    /**
     * Заменяет каждую ссылку изображения на [token]. Первый шаг загрузки
     * заметки в rich-редактор: markdown-парсер библиотеки портит документы
     * с картинками, поэтому текст парсится без них, а картинки вставляются
     * по одной на место токенов (см. setMarkdownWithImages).
     */
    fun tokenizeImageRefs(markdown: String, token: String): String =
        imageRef.replace(markdown) { token }
}
