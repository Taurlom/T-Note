package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.util.sharePhoto
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Полноэкранная галерея фотографий с горизонтальным свайпом.
 * Поддерживает зум, поворот (EXIF и пиксельный), шэринг и кроп
 * (кроп доступен только для документов, передаётся через [onCropComplete]).
 */
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
                                            withContext(Dispatchers.IO) {
                                                if (!rotateViaExifTag(file)) {
                                                    rotateImage(file)
                                                }
                                            }
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
                                            android.graphics.Bitmap.CompressFormat.JPEG, 100, out
                                        )
                                    }
                                    bitmap.recycle()
                                }
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