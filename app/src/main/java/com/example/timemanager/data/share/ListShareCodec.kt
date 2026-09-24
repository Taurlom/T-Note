package com.example.timemanager.data.share

import com.example.timemanager.domain.model.SharedList
import com.example.timemanager.domain.model.SharedTask
import org.json.JSONArray
import org.json.JSONObject

/**
 * Формат `.tnote`: JSON с магическим полем `tnoteList`.
 *
 * Чужой файл — недоверенные данные: поля срезаются до разумных лимитов,
 * пустые пункты пропускаются, неизвестные поля игнорируются, чужой формат
 * отклоняется исключением.
 */
object ListShareCodec {

    const val FORMAT_KEY = "tnoteList"
    const val FORMAT_VERSION = 1

    const val MAX_NAME_LENGTH = 120
    const val MAX_TASKS = 500
    const val MAX_TITLE_LENGTH = 200
    const val MAX_DESCRIPTION_LENGTH = 1_000

    fun encode(list: SharedList): String = JSONObject().apply {
        put(FORMAT_KEY, FORMAT_VERSION)
        put("name", list.name)
        put("color", list.color ?: JSONObject.NULL)
        put(
            "tasks",
            JSONArray().apply {
                list.tasks.forEach { task ->
                    put(
                        JSONObject().apply {
                            put("title", task.title)
                            put("description", task.description)
                            put("completed", task.completed)
                        }
                    )
                }
            }
        )
    }.toString(2)

    /** @throws org.json.JSONException если это не JSON; IllegalArgumentException если не `.tnote`. */
    fun decode(raw: String): SharedList {
        val json = JSONObject(raw)
        require(json.optInt(FORMAT_KEY, -1) == FORMAT_VERSION) {
            "Не файл списка T-Note"
        }
        val name = json.optString("name").trim().take(MAX_NAME_LENGTH)
        require(name.isNotEmpty()) { "В файле нет названия списка" }

        val tasksJson = json.optJSONArray("tasks") ?: JSONArray()
        require(tasksJson.length() <= MAX_TASKS) { "Слишком много пунктов в файле" }

        val tasks = buildList {
            for (index in 0 until tasksJson.length()) {
                val task = tasksJson.optJSONObject(index) ?: continue
                val title = task.optString("title").trim().take(MAX_TITLE_LENGTH)
                if (title.isEmpty()) continue
                add(
                    SharedTask(
                        title = title,
                        description = task.optString("description")
                            .trim()
                            .take(MAX_DESCRIPTION_LENGTH),
                        completed = task.optBoolean("completed", false)
                    )
                )
            }
        }
        return SharedList(
            name = name,
            color = (json.opt("color") as? Number)?.toLong(),
            tasks = tasks
        )
    }
}
