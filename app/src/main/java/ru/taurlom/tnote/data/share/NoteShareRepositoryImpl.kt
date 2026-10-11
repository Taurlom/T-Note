package ru.taurlom.tnote.data.share

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.taurlom.tnote.data.local.readFileInsideFilesDir
import ru.taurlom.tnote.domain.model.Note
import ru.taurlom.tnote.domain.model.NoteShareFormat
import ru.taurlom.tnote.domain.model.SharedNote
import ru.taurlom.tnote.domain.repository.NoteShareRepository
import ru.taurlom.tnote.domain.util.NoteImageRefs
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteShareRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) : NoteShareRepository {

    override suspend fun isNoteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(SNIFF_BYTES)
                val read = stream.read(buffer)
                read > 0 && String(buffer, 0, read, Charsets.UTF_8)
                    .contains("\"${NoteShareCodec.FORMAT_KEY}\"")
            } ?: false
        }.getOrDefault(false)
    }

    override suspend fun exportNote(note: Note): Uri = withContext(Dispatchers.IO) {
        FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            writeShareFile(note),
        )
    }

    /**
     * Запись файла шэринга — отдельно от FileProvider: unit-тестам
     * (Robolectric на Windows) FileProvider-roots недоступны, а вся
     * логика (сбор фото, guard путей, кодек) живёт здесь.
     */
    internal suspend fun writeShareFile(note: Note): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // Прошлые экспорты не нужны: URI живёт до отправки интентом.
        dir.listFiles()?.forEach { it.delete() }

        // Фото — по ссылкам текста (их порядок и есть порядок фото); путь
        // из базы читается только внутри filesDir (FilesDirGuard): база
        // могла прийти из чужого бэкапа, «../databases/…» не утечёт.
        val photos = NoteImageRefs.extractAll(note.content).mapNotNull { path ->
            context.readFileInsideFilesDir(path)
                ?.let { NoteShareCodec.SharedNotePhoto(path, it) }
        }
        return@withContext File(dir, sanitizeFileName(note.title) + NoteShareFormat.EXTENSION)
            .apply { writeText(NoteShareCodec.encode(note.title, note.content, note.tags, photos)) }
    }

    override suspend fun importFromFile(uri: Uri): SharedNote = withContext(Dispatchers.IO) {
        // Размер проверяем до чтения: файл приходит из чужого приложения.
        val statSize = context.contentResolver
            .openFileDescriptor(uri, "r")
            ?.use { it.statSize }
        require(statSize == null || statSize < 0 || statSize <= MAX_FILE_BYTES) {
            "Файл слишком большой"
        }
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: throw IllegalStateException("Файл недоступен для чтения")
        require(raw.length <= MAX_FILE_BYTES) { "Файл слишком большой" }
        val decoded = NoteShareCodec.decode(raw)

        // Фото — во временные файлы: AddNoteUseCase скопирует их в постоянное
        // хранилище и перепишет ссылки в тексте, как content:// из редактора.
        // Имена временных файлов наши (UUID): «name» из чужого файла идёт
        // только ключом замены, в файловую систему не попадает.
        val inbox = File(context.cacheDir, INBOX_DIR)
        inbox.deleteRecursively() // вчерашний неподтверждённый импорт не копится
        inbox.mkdirs()
        val replacements = mutableMapOf<String, String>()
        val tempUris = mutableListOf<String>()
        decoded.photos.forEach { photo ->
            val temp = File(inbox, "${UUID.randomUUID()}.jpg")
            temp.writeBytes(photo.data)
            val tempUri = Uri.fromFile(temp).toString()
            replacements[photo.name] = tempUri
            tempUris += tempUri
        }
        SharedNote(
            title = decoded.title,
            content = NoteImageRefs.replace(decoded.content, replacements),
            tags = decoded.tags,
            photoUris = tempUris,
        )
    }
}

private const val SHARE_DIR = "note_shares"
private const val INBOX_DIR = "note_share_inbox"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
private const val SNIFF_BYTES = 512

// JSON с фото в base64: 10 фото по 6 МБ + текст с запасом.
private const val MAX_FILE_BYTES = 100_000_000L
