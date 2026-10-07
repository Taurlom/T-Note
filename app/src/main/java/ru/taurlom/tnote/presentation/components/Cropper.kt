package ru.taurlom.tnote.presentation.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.taurlom.tnote.R
import kotlin.math.min

/** Режим перетаскивания в окне кропа. */
private enum class CropDragMode { None, Move, TopLeft, TopRight, BottomLeft, BottomRight }

/**
 * Экран кропа: уменьшенное фото, затемнение вне рамки, сетка третей,
 * перетаскивание рамки и её углов и подтверждение/отмена.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun Cropper(
    photoPath: String,
    refreshKey: Int,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onCropComplete: (Bitmap) -> Unit,
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
                bottom = bmp.height * 0.9f,
            ),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
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
            modifier = Modifier.fillMaxSize(),
        )

        // Затемнение вне рамки, сама рамка, сетка третей и ручки
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tl = Offset(
                contentLeft + selection.left * scale,
                contentTop + selection.top * scale,
            )
            val br = Offset(
                contentLeft + selection.right * scale,
                contentTop + selection.bottom * scale,
            )
            val w = br.x - tl.x
            val h = br.y - tl.y

            val scrim = Color.Black.copy(alpha = 0.5f)
            drawRect(scrim, Offset.Zero, Size(size.width, tl.y))
            drawRect(scrim, Offset(0f, br.y), Size(size.width, size.height - br.y))
            drawRect(scrim, Offset(0f, tl.y), Size(tl.x, h))
            drawRect(scrim, Offset(br.x, tl.y), Size(size.width - br.x, h))

            drawRect(Color.White, tl, Size(w, h), style = Stroke(width = 2f.dp.toPx()))

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
                        bottom = contentTop + selection.bottom * scale,
                    )

                    detectDragGestures(
                        onDragStart = { start ->
                            val view = viewRect()
                            val slop = 36f.dp.toPx()
                            val corners = listOf(
                                CropDragMode.TopLeft to Offset(view.left, view.top),
                                CropDragMode.TopRight to Offset(view.right, view.top),
                                CropDragMode.BottomLeft to Offset(view.left, view.bottom),
                                CropDragMode.BottomRight to Offset(view.right, view.bottom),
                            )
                            val corner = corners.minByOrNull { (_, pos) ->
                                (pos - start).getDistance()
                            }
                            mode = when {
                                corner != null && (corner.second - start).getDistance() <= slop -> corner.first
                                start.x >= view.left &&
                                    start.x <= view.right &&
                                    start.y >= view.top &&
                                    start.y <= view.bottom -> CropDragMode.Move
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
                                    sel.bottom,
                                )
                                CropDragMode.TopRight -> CropRect(
                                    sel.left,
                                    (sel.top + dy).coerceIn(0f, sel.bottom - CROP_MIN_SELECTION_PX),
                                    (sel.right + dx).coerceIn(sel.left + CROP_MIN_SELECTION_PX, imgW),
                                    sel.bottom,
                                )
                                CropDragMode.BottomLeft -> CropRect(
                                    (sel.left + dx).coerceIn(0f, sel.right - CROP_MIN_SELECTION_PX),
                                    sel.top,
                                    sel.right,
                                    (sel.bottom + dy).coerceIn(sel.top + CROP_MIN_SELECTION_PX, imgH),
                                )
                                CropDragMode.BottomRight -> CropRect(
                                    sel.left,
                                    sel.top,
                                    (sel.right + dx).coerceIn(sel.left + CROP_MIN_SELECTION_PX, imgW),
                                    (sel.bottom + dy).coerceIn(sel.top + CROP_MIN_SELECTION_PX, imgH),
                                )
                            }
                        },
                        onDragEnd = { mode = CropDragMode.None },
                        onDragCancel = { mode = CropDragMode.None },
                    )
                },
        )

        // Кнопки подтверждения/отмены
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }

                IconButton(
                    enabled = !decoding,
                    onClick = {
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
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "Confirm",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}
