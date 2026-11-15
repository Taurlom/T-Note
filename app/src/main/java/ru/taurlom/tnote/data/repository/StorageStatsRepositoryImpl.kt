package ru.taurlom.tnote.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.local.DocumentPhotoSaver
import ru.taurlom.tnote.data.local.NotePhotoSaver
import ru.taurlom.tnote.domain.model.StorageUsage
import ru.taurlom.tnote.domain.repository.StorageStatsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Размер файлов приложения по группам. Источники:
 *  - файлы: filesDir (каталоги фото note_photos/document_photos — свои
 *    группы, DataStore и остальное — «прочее»), каталог databases рядом
 *    с filesDir (база Room) и cacheDir/codeCacheDir (кэш);
 *  - код приложения: установленный APK (base + splits из ApplicationInfo).
 *
 * Не учитываются только внешние каталоги (externalCacheDir и т.п.) —
 * они живут на общем хранилище, а не во внутренней памяти приложения.
 */
@Singleton
class StorageStatsRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) : StorageStatsRepository {

    override suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(
            photos = dirUsage(File(context.filesDir, NotePhotoSaver.PHOTOS_DIR)),
            documents = dirUsage(File(context.filesDir, DocumentPhotoSaver.PHOTOS_DIR)),
            database = databaseUsage(),
            other = walkOther() +
                dirUsage(context.cacheDir) +
                dirUsage(context.codeCacheDir) +
                apkUsage(),
        )
    }

    /** Суммарный размер и число файлов каталога (рекурсивно); нет каталога — пусто. */
    private fun dirUsage(vararg dirs: File): StorageUsage.Group {
        var bytes = 0L
        var files = 0
        dirs.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach {
                bytes += it.length()
                files++
            }
        }
        return StorageUsage.Group(bytes = bytes, files = files)
    }

    /** Сложение групп: пригодится, когда «прочее» собирается из нескольких мест. */
    private operator fun StorageUsage.Group.plus(other: StorageUsage.Group) =
        StorageUsage.Group(bytes = bytes + other.bytes, files = files + other.files)

    /** Файлы базы Room: сама база плюс журналы wal/shm рядом с ней. */
    private fun databaseUsage(): StorageUsage.Group {
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        var bytes = 0L
        var files = 0
        listOf(dbFile, File(dbFile.path + DB_WAL_SUFFIX), File(dbFile.path + DB_SHM_SUFFIX))
            .forEach { file ->
                if (file.isFile) {
                    bytes += file.length()
                    files++
                }
            }
        return StorageUsage.Group(bytes = bytes, files = files)
    }

    /**
     * Код приложения: установленный APK. У поставщиков магазина (RuStore,
     * Play) это почти всегда split-установка: базовый apk плюс сплиты
     * (locale/abi/density) — считаем их все.
     */
    private fun apkUsage(): StorageUsage.Group {
        val appInfo = context.applicationInfo
        val apks = buildList {
            appInfo.sourceDir?.let { add(it) }
            appInfo.splitSourceDirs?.let { addAll(it) }
        }
        var bytes = 0L
        var files = 0
        apks.forEach { path ->
            val apk = File(path)
            if (apk.isFile) {
                bytes += apk.length()
                files++
            }
        }
        return StorageUsage.Group(bytes = bytes, files = files)
    }

    /**
     * «Прочее» из файлов данных: всё в filesDir, кроме каталогов фото.
     * Подкаталогов базы внутри filesDir нет (databases — сосед filesDir),
     * а каталоги фото известны точно: при появлении новых постоянных данных
     * рядом их нужно либо добавить отдельной группой, либо учесть здесь.
     */
    private fun walkOther(): StorageUsage.Group {
        var bytes = 0L
        var files = 0
        context.filesDir
            .walkTopDown()
            .onEnter { dir -> dir == context.filesDir || dir.name !in PHOTO_DIRS }
            .filter { it.isFile }
            .forEach { file ->
                bytes += file.length()
                files++
            }
        return StorageUsage.Group(bytes = bytes, files = files)
    }

    private companion object {
        const val DB_WAL_SUFFIX = "-wal"
        const val DB_SHM_SUFFIX = "-shm"

        val PHOTO_DIRS = setOf(NotePhotoSaver.PHOTOS_DIR, DocumentPhotoSaver.PHOTOS_DIR)
    }
}
