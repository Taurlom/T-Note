package com.example.timemanager.domain.repository

import android.net.Uri
import com.example.timemanager.domain.model.SharedList

/**
 * Переносимый формат `.tnote`: экспорт списка в файл для отправки и
 * чтение/валидация файла, полученного с другого устройства.
 */
interface ListShareRepository {

    /** Пишет список во временный файл и возвращает `content://` URI для вложения. */
    suspend fun exportToFile(sharedList: SharedList): Uri

    /**
     * Читает и валидирует файл. Бросает исключение, если это не копия
     * списка T-Note или данные не проходят лимиты.
     */
    suspend fun importFromFile(uri: Uri): SharedList
}
