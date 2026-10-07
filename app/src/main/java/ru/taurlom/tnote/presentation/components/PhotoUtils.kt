package ru.taurlom.tnote.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Потолок длинной стороны фото для показа в окне кропа: защита от OOM. */
internal const val CROP_DISPLAY_MAX_DIM = 2048

/** Минимальный размер рамки кропа в пикселях показанного bitmap. */
internal const val CROP_MIN_SELECTION_PX = 56f

/** Потолок длинной стороны результата кропа: защита от OOM на гигантских фото. */
private const val CROP_OUTPUT_MAX_DIM = 4096

/** Простой прямоугольник в координатах bitmap: left/top/right/bottom. */
internal data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Показываемое для кропа уменьшенное фото (уже прямое, по тегу) плюс размеры
 * и ориентация «сырого» оригинала: по ним рамка пересчитывается в пиксели
 * файла — region-декодер тег не применяет и видит пиксели как лежат.
 */
internal data class SampledPhoto(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val orientation: Int
)

internal fun decodeSampledBitmap(path: String, maxDim: Int): SampledPhoto? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
        sample *= 2
    }
    val decoded = runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull() ?: return null

    // BitmapFactory игнорирует EXIF-ориентацию — окно кропа должно показывать
    // то же, что пользователь видит в галерее (там тег применяет Coil).
    val orientation = readOrientation(File(path))
    return SampledPhoto(
        bitmap = applyOrientation(decoded, orientation),
        sourceWidth = bounds.outWidth,
        sourceHeight = bounds.outHeight,
        orientation = orientation
    )
}

internal fun readOrientation(file: File): Int = runCatching {
    ExifInterface(file.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/** Поворачивает уже декодированный (малый) битмап по EXIF-тегу. */
internal fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return source
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (rotated != source) source.recycle()
    return rotated
}

/**
 * Поворот на 90° правкой EXIF Orientation: байты JPEG не трогаются — ни
 * декодирования, ни пережатия, ни расхода памяти (прежний путь выкладывал
 * весь bitmap в RAM и заново кодировал JPEG). 1→6→3→8→1.
 * Возвращает false для зеркальных значений тега — вызывающий пойдёт
 * пиксельным путём.
 */
internal fun rotateViaExifTag(file: File): Boolean {
    val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return false
    val current = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_UNDEFINED
    )
    val next = when (current) {
        ExifInterface.ORIENTATION_UNDEFINED,
        ExifInterface.ORIENTATION_NORMAL -> ExifInterface.ORIENTATION_ROTATE_90
        ExifInterface.ORIENTATION_ROTATE_90 -> ExifInterface.ORIENTATION_ROTATE_180
        ExifInterface.ORIENTATION_ROTATE_180 -> ExifInterface.ORIENTATION_ROTATE_270
        ExifInterface.ORIENTATION_ROTATE_270 -> ExifInterface.ORIENTATION_NORMAL
        else -> return false
    }
    return runCatching {
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, next.toString())
        exif.saveAttributes()
    }.isSuccess
}

/**
 * Вырезает область из оригинала в полном разрешении: нормализованные
 * координаты рамки (0..1 прямого показанного bitmap) пересчитываются в
 * «сырые» пиксели источника с учётом EXIF-ориентации, [BitmapRegionDecoder]
 * декодирует только этот прямоугольник. Результат поднимается тегом, чтобы
 * сохранённый кроп был прямым сам по себе.
 *
 * Гигантская область сэмплируется до [CROP_OUTPUT_MAX_DIM] — защита от OOM.
 * Зеркальные ориентации и форматы без region-декода возвращают null —
 * вызывающий откатывается к кропу из уменьшенной копии.
 */
internal fun decodeRegionCrop(
    path: String,
    selection: CropRect,
    photo: SampledPhoto
): Bitmap? {
    val dw = photo.bitmap.width.toFloat()
    val dh = photo.bitmap.height.toFloat()
    if (dw <= 0f || dh <= 0f) return null
    val nl = (selection.left / dw).coerceIn(0f, 1f)
    val nt = (selection.top / dh).coerceIn(0f, 1f)
    val nr = (selection.right / dw).coerceIn(0f, 1f)
    val nb = (selection.bottom / dh).coerceIn(0f, 1f)
    val sw = photo.sourceWidth
    val sh = photo.sourceHeight

    val rawRect = when (photo.orientation) {
        // показ = raw, повёрнутый на 90° по часовой: x = v·W, y = (1−u)·H
        ExifInterface.ORIENTATION_ROTATE_90 -> Rect(
            (nt * sw).roundToInt(), ((1f - nr) * sh).roundToInt(),
            (nb * sw).roundToInt(), ((1f - nl) * sh).roundToInt()
        )
        ExifInterface.ORIENTATION_ROTATE_180 -> Rect(
            ((1f - nr) * sw).roundToInt(), ((1f - nb) * sh).roundToInt(),
            ((1f - nl) * sw).roundToInt(), ((1f - nt) * sh).roundToInt()
        )
        // показ = raw, повёрнутый на 270° по часовой: x = (1−v)·W, y = u·H
        ExifInterface.ORIENTATION_ROTATE_270 -> Rect(
            ((1f - nb) * sw).roundToInt(), (nl * sh).roundToInt(),
            ((1f - nt) * sw).roundToInt(), (nr * sh).roundToInt()
        )
        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> Rect(
            (nl * sw).roundToInt(), (nt * sh).roundToInt(),
            (nr * sw).roundToInt(), (nb * sh).roundToInt()
        )
        // Зеркальные варианты: эта математика осей не годится — откат на копию.
        else -> return null
    }
    val left = rawRect.left.coerceIn(0, sw - 1)
    val top = rawRect.top.coerceIn(0, sh - 1)
    val right = rawRect.right.coerceIn(left + 1, sw)
    val bottom = rawRect.bottom.coerceIn(top + 1, sh)

    var sample = 1
    while (max(right - left, bottom - top) / (sample * 2) >= CROP_OUTPUT_MAX_DIM) {
        sample *= 2
    }

    return try {
        val decoder = BitmapRegionDecoder.newInstance(path)
        try {
            val region = decoder.decodeRegion(
                Rect(left, top, right, bottom),
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
            region?.let { applyOrientation(it, photo.orientation) }
        } finally {
            decoder.recycle()
        }
    } catch (e: Exception) {
        null
    }
}

internal fun cropBitmap(source: Bitmap, selection: CropRect): Bitmap {
    val x = selection.left.roundToInt().coerceIn(0, source.width - 1)
    val y = selection.top.roundToInt().coerceIn(0, source.height - 1)
    val width = selection.width.roundToInt().coerceIn(1, source.width - x)
    val height = selection.height.roundToInt().coerceIn(1, source.height - y)
    return Bitmap.createBitmap(source, x, y, width, height)
}

/**
 * Пиксельный поворот — запасной путь для зеркальных EXIF-ориентаций
 * (встречаются редко: камера пишет 1/6/3/8, теговый поворот их не создаёт).
 */
internal fun rotateImage(file: File) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return

    val matrix = Matrix().apply {
        postRotate(90f)
    }

    val rotatedBitmap = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
    )

    FileOutputStream(file).use { out ->
        rotatedBitmap.compress(
            android.graphics.Bitmap.CompressFormat.JPEG, 100, out
        )
    }

    if (rotatedBitmap != bitmap) {
        bitmap.recycle()
    }
    rotatedBitmap.recycle()
}