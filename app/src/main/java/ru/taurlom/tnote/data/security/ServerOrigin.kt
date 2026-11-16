package ru.taurlom.tnote.data.security

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val HTTPS_SCHEME = "https://"

/**
 * Разбор адреса в origin — «scheme://host[:порт]» (порт только
 * нестандартный; путь и запрос отбрасываются). Null — строка не абсолютный
 * http(s)-URL: формы Google («calendarId/eventId») не URL, для них это норма.
 *
 * Это граница, за которую учётные данные не выпускаются: «тот же логин» ещё
 * не значит «тот же сервер», а адрес сервера и адреса объектов живут в базе —
 * база же приходит из резервной копии, поэтому прежде, чем отдать пароль,
 * спрашиваем у адреса его origin и сравниваем с тем, которому пароль
 * принадлежал (привязка ключа — PasswordStore, адреса —
 * CaldavRepositoryImpl.passwordFor, CaldavSyncEngine.requireAccountOrigin и
 * CaldavClient.resolve).
 */
fun originOf(url: String): String? = url.toHttpUrlOrNull()?.let { target ->
    val port = if (target.port == defaultPortOf(target.scheme)) "" else ":${target.port}"
    "${target.scheme}://${target.host}$port"
}

/**
 * Origin https-адреса; null — http (пароль ушёл бы открытым текстом), иная
 * схема или неразбираемая строка. Единая точка отказа для всякого запроса с
 * подставляемым секретом (Basic-пароль CalDAV, Bearer Google).
 */
fun httpsOriginOf(url: String): String? = originOf(url)?.takeIf { it.startsWith(HTTPS_SCHEME, ignoreCase = true) }

/** Порт по умолчанию для схемы; прочее считается нестандартным и входит в origin. */
private fun defaultPortOf(scheme: String): Int = when (scheme) {
    "https" -> 443
    "http" -> 80
    else -> -1
}
