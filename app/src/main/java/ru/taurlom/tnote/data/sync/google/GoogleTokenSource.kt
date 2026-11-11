package ru.taurlom.tnote.data.sync.google

import android.content.Context
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.taurlom.tnote.domain.repository.CaldavConsentRequired
import ru.taurlom.tnote.domain.repository.CaldavSyncException
import java.io.IOException
import javax.inject.Inject

/**
 * Scope Google Calendar API через пробел: правка событий (calendar.events)
 * плюс чтение списка календарей (calendar.readonly) — calendarList.list без
 * него отвечает 403 «insufficient authentication scopes».
 */
const val GOOGLE_CALENDAR_SCOPE =
    "oauth2:https://www.googleapis.com/auth/calendar.events" +
        " https://www.googleapis.com/auth/calendar.readonly"

/** Точка получения OAuth-токена — для подмены в юнит-тестах адаптера. */
interface GoogleTokenSource {

    /**
     * Токен доступа для [accountName] (короткоживущий; GoogleAuthUtil сам
     * кеширует и молча обновляет). Бросает [CaldavSyncException.Auth] с
     * намерением подтверждения в [CaldavConsentRequired] — его умеет
     * ловить только экран с Activity: из фонового воркера диалог согласия
     * показать некому.
     */
    suspend fun token(accountName: String): String
}

/**
 * Продакшен-источник: GoogleAuthUtil (Play Services) против Android-клиента
 * OAuth (package + SHA-1). Отдельных refresh-токенов приложение не хранит:
 * getToken сам возвращает живой токен из кеша или обновляет его.
 */
class GoogleAuthUtilTokenSource @Inject constructor(@ApplicationContext private val context: Context) : GoogleTokenSource {

    override suspend fun token(accountName: String): String = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.getToken(context, accountName, GOOGLE_CALENDAR_SCOPE)
        } catch (e: UserRecoverableAuthException) {
            // Согласие не выдано (первое подключение или отзыв): экран
            // показывает намерение — Activity запускает диалог Google.
            throw CaldavConsentRequired(e.intent)
        } catch (e: GoogleAuthException) {
            throw CaldavSyncException.Auth(e.message ?: "Google отклонил авторизацию")
        } catch (e: IOException) {
            throw CaldavSyncException.Network(e)
        }
    }
}
