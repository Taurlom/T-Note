package ru.taurlom.tnote.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.taurlom.tnote.domain.model.AppUpdateResult
import ru.taurlom.tnote.domain.repository.AppUpdateRepository
import ru.taurlom.tnote.domain.util.compareVersionNames
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация проверки обновлений: последняя опубликованная версия читается
 * с публичной страницы каталога RuStore (HTML + JSON-LD, см.
 * [RuStoreCatalogParser]). Запрос — OkHttp, который уже есть в проекте,
 * новых зависимостей не требуется. Сеть — Dispatchers.IO: вызовы не должны
 * попадать в main-поток.
 *
 * Суточное окно (DataStore): частые проверки не долбят каталог. Отметка
 * времени ставится только после успешного разбора — при сбое сети или
 * смене формата страницы следующая проверка не откладывается на сутки.
 */
@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : AppUpdateRepository {

    private val okhttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun check(force: Boolean): AppUpdateResult = withContext(Dispatchers.IO) {
        val installed = installedVersionName()
        // Окно ещё не истекло — сеть не трогаем, отвечаем сохранённым
        // результатом (он перепроверяется против установленной версии:
        // приложение могли обновить в магазине с прошлой проверки).
        val lastSuccess = dataStore.data.first()[LAST_CHECK_KEY] ?: 0L
        if (!force && clock.millis() - lastSuccess < THROTTLE_MILLIS) {
            return@withContext persistedResult(installed)
        }
        val html = try {
            fetchCatalogPage()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext AppUpdateResult.Unreachable
        }
        val latest = RuStoreCatalogParser.parseLatestVersionName(html)
            ?: return@withContext AppUpdateResult.Unreachable
        dataStore.edit { it[LAST_CHECK_KEY] = clock.millis() }
        if (compareVersionNames(latest, installed) > 0) {
            dataStore.edit { it[AVAILABLE_KEY] = latest }
            AppUpdateResult.Available(latest)
        } else {
            dataStore.edit { it.remove(AVAILABLE_KEY) }
            AppUpdateResult.UpToDate
        }
    }

    override fun storePageUrl(): String = CATALOG_URL_PREFIX + context.packageName

    override val skippedVersion: Flow<String> =
        dataStore.data.map { it[SKIPPED_KEY] ?: "" }

    override suspend fun skip(versionName: String) {
        dataStore.edit { it[SKIPPED_KEY] = versionName }
    }

    /**
     * Сохранённый без сетевого обращения результат: «доступная» версия
     * всё ещё актуальна, только если новее текущей установленной.
     */
    private suspend fun persistedResult(installed: String): AppUpdateResult {
        val available = dataStore.data.first()[AVAILABLE_KEY] ?: return AppUpdateResult.UpToDate
        return if (compareVersionNames(available, installed) > 0) {
            AppUpdateResult.Available(available)
        } else {
            AppUpdateResult.UpToDate
        }
    }

    private fun installedVersionName(): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    private fun fetchCatalogPage(): String {
        val request = Request.Builder()
            .url(storePageUrl())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        okhttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Каталог RuStore ответил ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val CATALOG_URL_PREFIX = "https://www.rustore.ru/catalog/app/"
        const val USER_AGENT = "T-Note update-check"
        const val TIMEOUT_SECONDS = 10L
        const val THROTTLE_MILLIS = 24L * 60 * 60 * 1000

        val LAST_CHECK_KEY = longPreferencesKey("update_last_check")
        val AVAILABLE_KEY = stringPreferencesKey("update_available_version")
        val SKIPPED_KEY = stringPreferencesKey("update_skipped_version")
    }
}
