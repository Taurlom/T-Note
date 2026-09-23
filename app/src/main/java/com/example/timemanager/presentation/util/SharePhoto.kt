package com.example.timemanager.presentation.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.timemanager.R
import java.io.File

/**
 * Поделиться фотографией через стандартный `ACTION_SEND`-интент: система
 * покажет все приложения, принимающие изображения, — Telegram, MAX, почту
 * и т.д., без отдельной поддержки каждого мессенджера.
 *
 * Файл лежит во внутренней памяти, недоступной другим приложениям,
 * поэтому отдаётся `content://`-URI из FileProvider с разовым правом чтения.
 */
fun sharePhoto(context: Context, file: File) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.share_photo))
    )
}
