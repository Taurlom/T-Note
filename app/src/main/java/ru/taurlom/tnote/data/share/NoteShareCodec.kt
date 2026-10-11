package ru.taurlom.tnote.data.share

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Формат `.tnote` для одиночной заметки: JSON с магическим полем
 * `tnoteNote`. Фото едут base64 внутри того же файла — один файл
 * удобнее переслать, чем архив.
 *
 * Магическое поле пишется ПЕРВЫМ: по первым байтам входной файл
 * отличают от `.tnote`-списка без полного чтения
 * (NoteShareRepository.isNoteFile).
 *
 * Чужой файл — недоверенные данные: поля срезаются до разумных лимитов,
 * неизвестные поля игнорируются, чужой формат отклоняется исключением.
 */
object NoteShareCodec {

    const val FORMAT_KEY = "tnoteNote"
    const val FORMAT_VERSION = 1

    const val MAX_TITLE_LENGTH = 200
    const val MAX_CONTENT_LENGTH = 200_000
    const val MAX_TAGS = 50
    const val MAX_TAG_LENGTH = 50
    const val MAX_PHOTOS = 10
    const val MAX_PHOTO_BYTES = 6_000_000

    /**
     * Фото внутри файла: [name] — относительный путь-ссылка из текста
     * (`note_photos/uuid.jpg`). Это ключ для переписи ссылок при импорте,
     * в файловую систему имя не попадает.
     */
    class SharedNotePhoto(val name: String, val data: ByteArray)

    class DecodedNote(val title: String, val content: String, val tags: List<String>, val photos: List<SharedNotePhoto>)

    fun encode(title: String, content: String, tags: List<String>, photos: List<SharedNotePhoto>): String = JSONObject().apply {
        put(FORMAT_KEY, FORMAT_VERSION)
        put("title", title)
        put("content", content)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        put(
            "photos",
            JSONArray().apply {
                photos.forEach { photo ->
                    put(
                        JSONObject().apply {
                            put("name", photo.name)
                            put("data", Base64.getEncoder().encodeToString(photo.data))
                        },
                    )
                }
            },
        )
    }.toString(2)

    /** @throws org.json.JSONException если это не JSON; IllegalArgumentException если не `.tnote`-заметка. */
    fun decode(raw: String): DecodedNote {
        val json = JSONObject(raw)
        require(json.optInt(FORMAT_KEY, -1) == FORMAT_VERSION) {
            "Не файл заметки T-Note"
        }
        val title = json.optString("title").trim().take(MAX_TITLE_LENGTH)
        require(title.isNotEmpty()) { "В файле нет заголовка заметки" }
        val content = json.optString("content").take(MAX_CONTENT_LENGTH)

        val tagsJson = json.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (index in 0 until minOf(tagsJson.length(), MAX_TAGS)) {
                val tag = tagsJson.optString(index).trim().take(MAX_TAG_LENGTH)
                if (tag.isNotEmpty()) add(tag)
            }
        }

        val photosJson = json.optJSONArray("photos") ?: JSONArray()
        require(photosJson.length() <= MAX_PHOTOS) { "Слишком много фото в файле" }
        val photos = buildList {
            for (index in 0 until photosJson.length()) {
                val photo = photosJson.optJSONObject(index) ?: continue
                val name = photo.optString("name").trim()
                val data = photo.optString("data")
                if (name.isEmpty() || data.isEmpty()) continue
                // Битый base64 одного фото не хоронит заметку целиком.
                val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull() ?: continue
                require(bytes.size <= MAX_PHOTO_BYTES) { "Фото слишком большое" }
                add(SharedNotePhoto(name, bytes))
            }
        }
        return DecodedNote(title, content, tags, photos)
    }
}
