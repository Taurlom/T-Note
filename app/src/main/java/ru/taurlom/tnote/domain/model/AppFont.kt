package ru.taurlom.tnote.domain.model

/**
 * Шрифты интерфейса. Выбор хранится в настройках (DataStore) и в манифесте
 * резервной копии по [name] — поэтому enum живёт в domain, а не в UI.
 * Привязка к FontFamily — presentation-концерн: extension `fontFamily`
 * в presentation/theme/Type.kt.
 */
enum class AppFont(val displayName: String) {
    PT_SANS("PT Sans"),
    OPEN_SANS("Open Sans"),
    ROBOTO("Roboto"),
    INTER("Inter"),
    NUNITO("Nunito"),
    LATO("Lato"),
    LOBSTER("Lobster"),
    JONOVA("Jonova"),
    MAZZARD("Mazzard"),
    RUBIK("Rubik");

    companion object {
        fun fromName(name: String): AppFont = entries.find { it.name == name } ?: PT_SANS
    }
}
