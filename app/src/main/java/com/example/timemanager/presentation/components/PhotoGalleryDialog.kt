package com.example.timemanager.presentation.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.timemanager.R
import com.example.timemanager.presentation.util.sharePhoto
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CROP_DISPLAY_MAX_DIM = 2048
private const val CROP_MIN_SELECTION_PX = 56f

/** Потолок длинной стороны результата кропа: защита от OOM на гигантских фото. */
private const val CROP_OUTPUT_MAX_DIM = 4096

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGalleryDialog(
    photoPaths: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onCropComplete: ((oldPath: String, newPath: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photoPaths.size })
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showCropDialog by remember { mutableStateOf(false) }
    var currentPhotoPath by remember { mutableStateOf("") }
    // Поворот и сохранение кропа — тяжёлый bitmap-IO; флаг занятости держит
    // кнопки от двойного тапа, пока операция не дошла до диска.
    var busy by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        currentPhotoPath = photoPaths[pagerState.currentPage]

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    sharePhoto(
                                        context,
                                        File(context.filesDir, currentPhotoPath)
                                    )
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_share),
                                    contentDescription = stringResource(R.string.share_photo),
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    val file = File(context.filesDir, currentPhotoPath)
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { rotateImage(file) }
                                            refreshTrigger++
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_rotate_right),
                                    contentDescription = stringResource(R.string.rotate),
                                    tint = Color.White
                                )
                            }

                            // Кроп пишет новый файл в document_photos и требует
                            // колбэка на замену пути — доступен только там, где
                            // его обрабатывают (документы).
                            if (onCropComplete != null) {
                                IconButton(
                                    enabled = !busy,
                                    onClick = { showCropDialog = true }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_crop),
                                        contentDescription = stringResource(R.string.crop),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            if (showCropDialog) {
                Cropper(
                    photoPath = File(context.filesDir, currentPhotoPath).absolutePath,
                    refreshKey = refreshTrigger,
                    modifier = Modifier.padding(padding),
                    onDismiss = { showCropDialog = false },
                    onCropComplete = { bitmap ->
                        showCropDialog = false

                        // Сохраняем обрезанное изображение в ту же папку, что
                        // и остальные фото документов. Запись — на IO: JPEG
                        // full-size может весить десятки МБ.
                        val oldPath = currentPhotoPath
                        val originalFileName = oldPath.substringAfterLast("/")
                        val photosDir = File(context.filesDir, "document_photos").apply { mkdirs() }
                        val newFile = File(photosDir, "cropped_${System.currentTimeMillis()}_$originalFileName")
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    FileOutputStream(newFile).use { out ->
                                        bitmap.compress(
                                            android.graphics.Bitmap.CompressFormat.JPEG,
                                            100,
                                            out
                                        )
                                    }
                                    bitmap.recycle()
                                }
                                // Вызываем callback для обновления пути
                                // (относительный путь, как у остальных фото)
                                // и закрываем диалог уже после записи на диск.
                                onCropComplete?.invoke(oldPath, "document_photos/${newFile.name}")
                                onDismiss()
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) { page ->
                    ZoomableImage(
                        model = File(context.filesDir, photoPaths[page]).toUri(),
                        contentDescription = stringResource(R.string.document_photo),
                        refreshKey = refreshTrigger
                    )
                }
            }
        }
    }
}

private enum class CropDragMode { None, Move, TopLeft, TopRight, BottomLeft, BottomRight }

/** Простой прямоугольник в координатах bitmap (left/top/right/bottom), т.к. FloatRect недоступен в этой версии Compose UI */
private data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun Cropper(
    photoPath: String,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onCropComplete: (Bitmap) -> Unit
) {
    var photo by remember { mutableStateOf<SampledPhoto?>(null) }
    var decoding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(photoPath, refreshKey) {
        decoding = true
        photo = withContext(Dispatchers.IO) {
            decodeSampledBitmap(photoPath, CROP_DISPLAY_MAX_DIM)
        }
        decoding = false
    }

    val bmp = photo?.bitmap
    if (bmp == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Область выделения в пикселях показываемого bitmap
    var selection by remember(bmp) {
        mutableStateOf(
            CropRect(
                left = bmp.width * 0.1f,
                top = bmp.height * 0.1f,
                right = bmp.width * 0.9f,
                bottom = bmp.height * 0.9f
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val scale = min(containerWidth / bmp.width, containerHeight / bmp.height)
        val contentLeft = (containerWidth - bmp.width * scale) / 2f
        val contentTop = (containerHeight - bmp.height * scale) / 2f

        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = stringResource(R.string.crop),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Затемнение вне рамки, сама рамка и ручки
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tl = Offset(contentLeft + selection.left * scale, contentTop + selection.top * scale)
            val br = Offset(contentLeft + selection.right * scale, contentTop + selection.bottom * scale)
            val w = br.x - tl.x
            val h = br.y - tl.y

            val scrim = Color.Black.copy(alpha = 0.5f)
            drawRect(scrim, Offset.Zero, Size(size.width, tl.y))
            drawRect(scrim, Offset(0f, br.y), Size(size.width, size.height - br.y))
            drawRect(scrim, Offset(0f, tl.y), Size(tl.x, h))
            drawRect(scrim, Offset(br.x, tl.y), Size(size.width - br.x, h))

            drawRect(Color.White, tl, Size(w, h), style = Stroke(width = 2f.dp.toPx()))

            // Сетка «правило третей»
            val grid = Color.White.copy(alpha = 0.35f)
            for (i in 1..2) {
                drawLine(grid, Offset(tl.x + w * i / 3f, tl.y), Offset(tl.x + w * i / 3f, br.y), 1f.dp.toPx())
                drawLine(grid, Offset(tl.x, tl.y + h * i / 3f), Offset(br.x, tl.y + h * i / 3f), 1f.dp.toPx())
            }

            val corners = listOf(tl, Offset(br.x, tl.y), Offset(tl.x, br.y), br)
            val handleRadius = 7f.dp.toPx()
            corners.forEach { corner ->
                drawCircle(Color.White, radius = handleRadius, center = corner)
            }
        }

        // Жесты: перетаскивание рамки и изменение размера за углы
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bmp, containerWidth, containerHeight) {
                    var mode = CropDragMode.None

                    fun viewRect(): CropRect = CropRect(
                        left = contentLeft + selection.left * scale,
                        top = contentTop + selection.top * scale,
                        right = contentLeft + selection.right * scale,
                        bottom = contentTop + selection.bottom * scale
                    )

                    detectDragGestures(
                        onDragStart = { start ->
                            val view = viewRect()
                            val slop = 36f.dp.toPx()
                            val corners = listOf(
                                CropDragMode.TopLeft to Offset(view.left, view.top),
                                CropDragMode.TopRight to Offset(view.right, view.top),
                                CropDragMode.BottomLeft to Offset(view.left, view.bottom),
                                CropDragMode.BottomRight to Offset(view.right, view.bottom)
                            )
                            val corner = corners.minByOrNull { (_, pos) -> (pos - start).getDistance() }
                            mode = when {
                                corner != null && (corner.second - start).getDistance() <= slop -> corner.first
                                start.x >= view.left && start.x <= view.right &&
                                    start.y >= view.top && start.y <= view.bottom -> CropDragMode.Move
                                else -> CropDragMode.None
                            }
                        },
                        onDrag = { _, dragAmount ->
                            val dx = dragAmount.x / scale
                            val dy = dragAmount.y / scale
                            val sel = selection
                            val imgW = bmp.width.toFloat()
                            val imgH = bmp.height.toFloat()
                            selection = when (mode) {
                                CropDragMode.None -> sel
                                CropDragMode.Move -> {
                                    val newLeft = (sel.left + dx).coerceIn(0f, (imgW - sel.width).coerceAtLeast(0f))
                                    val newTop = (sel.top + dy).coerceIn(0f, (imgH - sel.height).coerceAtLeast(0f))
                                    CropRect(newLeft, newTop, newLeft + sel.width, newTop + sel.height)
                                }
                                CropDragMode.TopLeft -> CropRect(
                                    (sel.left + dx).coerceIn(0f, sel.right - CROP_MIN_SELECTION_PX),
                                    (sel.top + dy).coerceIn(0f, sel.bottom - CROP_MIN_SELECTION_PX),
                                    sel.right,
                                    sel.bottom
                                )
                                CropDragMode.TopRight -> CropRect(
                                    sel.left,
                                    (sel.top + dy).coerceIn(0f, sel.bottom - CROP_MIN_SELECTION_PX),
                                    (sel.right + dx).coerceIn(sel.left + CROP_MIN_SELECTION_PX, imgW),
                                    sel.bottom
                                )
                                CropDragMode.BottomLeft -> CropRect(
                                    (sel.left + dx).coerceIn(0f, sel.right - CROP_MIN_SELECTION_PX),
                                    sel.top,
                                    sel.right,
                                    (sel.bottom + dy).coerceIn(sel.top + CROP_MIN_SELECTION_PX, imgH)
                                )
                                CropDragMode.BottomRight -> CropRect(
                                    sel.left,
                                    sel.top,
                                    (sel.right + dx).coerceIn(sel.left + CROP_MIN_SELECTION_PX, imgW),
                                    (sel.bottom + dy).coerceIn(sel.top + CROP_MIN_SELECTION_PX, imgH)
                                )
                            }
                        },
                        onDragEnd = { mode = CropDragMode.None },
                        onDragCancel = { mode = CropDragMode.None }
                    )
                }
        )

        // Кнопки подтверждения/отмены
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                IconButton(
                    enabled = !decoding,
                    onClick = {
                        // Полный расчёт — вне UI: регион-декод читает файл и
                        // декодирует область оригинала в полном разрешении.
                        scope.launch {
                            val cropped = withContext(Dispatchers.IO) {
                                val sampled = photo
                                if (sampled != null) {
                                    decodeRegionCrop(photoPath, selection, sampled)
                                        ?: cropBitmap(sampled.bitmap, selection)
                                } else {
                                    null
                                }
                            }
                            if (cropped != null) onCropComplete(cropped)
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "Confirm",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

private fun decodeSampledBitmap(path: String, maxDim: Int): SampledPhoto? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) {
        sample *= 2
    }
    val bitmap = runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull() ?: return null
    return SampledPhoto(
        bitmap = bitmap,
        sourceWidth = bounds.outWidth,
        sourceHeight = bounds.outHeight
    )
}

/**
 * Показываемое для кропа уменьшенное фото плюс размеры оригинала: по ним
 * рамка выделения пересчитывается в пиксели исходного файла.
 */
private data class SampledPhoto(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int
)

/**
 * Вырезает область из оригинала в полном разрешении: координаты рамки
 * (пиксели показанного bitmap) масштабируются до пикселей источника,
 * [BitmapRegionDecoder] декодирует только этот прямоугольник — не весь
 * файл и не уменьшенную копию, как было раньше (кроп резал 2048-пиксельный
 * даунсемпл и безвозвратно терял разрешение).
 *
 * Гигантская область сэмплируется до [CROP_OUTPUT_MAX_DIM] — защита от OOM
 * на 100-мегапиксельных панорамах. Форматы без region-декода возвращают
 * null — вызывающий откатывается к кропу из уменьшенной копии.
 */
@Suppress("DEPRECATION")
private fun decodeRegionCrop(
    path: String,
    selection: CropRect,
    photo: SampledPhoto
): Bitmap? {
    val scaleX = photo.sourceWidth.toFloat() / photo.bitmap.width
    val scaleY = photo.sourceHeight.toFloat() / photo.bitmap.height
    val left = (selection.left * scaleX).roundToInt().coerceIn(0, photo.sourceWidth - 1)
    val top = (selection.top * scaleY).roundToInt().coerceIn(0, photo.sourceHeight - 1)
    val right = (selection.right * scaleX).roundToInt().coerceIn(left + 1, photo.sourceWidth)
    val bottom = (selection.bottom * scaleY).roundToInt().coerceIn(top + 1, photo.sourceHeight)
    val rect = Rect(left, top, right, bottom)

    var sample = 1
    while (max(rect.width(), rect.height()) / (sample * 2) >= CROP_OUTPUT_MAX_DIM) {
        sample *= 2
    }

    // В API-стабах 34 у декодера есть только recycle(); на живых устройствах
    // 24–30 newInstance(String) — deprecated-перегрузка, возвращающая тот же
    // объект, recycle() на них тоже есть.
    return try {
        val decoder = BitmapRegionDecoder.newInstance(path)
        try {
            decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            decoder.recycle()
        }
    } catch (e: Exception) {
        null
    }
}

private fun cropBitmap(source: Bitmap, selection: CropRect): Bitmap {
    val x = selection.left.roundToInt().coerceIn(0, source.width - 1)
    val y = selection.top.roundToInt().coerceIn(0, source.height - 1)
    val width = selection.width.roundToInt().coerceIn(1, source.width - x)
    val height = selection.height.roundToInt().coerceIn(1, source.height - y)
    return Bitmap.createBitmap(source, x, y, width, height)
}

@Composable
private fun ZoomableImage(
    model: Any,
    contentDescription: String?,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    maxScale: Float = 5f
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, maxScale)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .setParameter("refresh", refreshKey)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

private fun rotateImage(file: File) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return

    val matrix = Matrix().apply {
        postRotate(90f)
    }

    val rotatedBitmap = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )

    FileOutputStream(file).use { out ->
        rotatedBitmap.compress(
            android.graphics.Bitmap.CompressFormat.JPEG,
            100,
            out
        )
    }

    if (rotatedBitmap != bitmap) {
        bitmap.recycle()
    }
    rotatedBitmap.recycle()
}
