package com.example.timemanager.presentation.components

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.timemanager.R
import com.example.timemanager.domain.model.Document
import com.example.timemanager.presentation.theme.AppTheme
import java.io.File

private const val MAX_PHOTOS = 4
private const val CAMERA_CAPTURES_DIR = "camera_captures"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentInputDialog(
    document: Document? = null,
    onDismiss: () -> Unit,
    onConfirm: (document: Document, photoUris: List<Uri>, removedPhotoPaths: List<String>) -> Unit
) {
    val isEdit = document != null

    var title by remember { mutableStateOf(document?.title.orEmpty()) }
    var description by remember { mutableStateOf(document?.description.orEmpty()) }

    val existingPhotoPaths = remember(document) { document?.photoPaths ?: emptyList() }
    val removedExistingPaths = remember { mutableStateListOf<String>() }
    val newPhotoUris = remember { mutableStateListOf<Uri>() }

    val context = LocalContext.current
    val isCameraAvailable = remember {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }

    // Путь к файлу для съёмки камерой; переживает перезапуск процесса ради камеры
    var pendingCaptureUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PHOTOS)
    ) { uris ->
        val availableSlots = MAX_PHOTOS - existingPhotoPaths.size + removedExistingPaths.size - newPhotoUris.size
        newPhotoUris.addAll(uris.take(availableSlots.coerceAtLeast(0)))
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCaptureUriString?.toUri()
        pendingCaptureUriString = null
        if (success && capturedUri != null) {
            val availableSlots = MAX_PHOTOS - existingPhotoPaths.size + removedExistingPaths.size - newPhotoUris.size
            if (availableSlots > 0) {
                newPhotoUris.add(capturedUri)
            }
        }
    }

    fun launchCameraCapture() {
        val capturesDir = File(context.cacheDir, CAMERA_CAPTURES_DIR).apply { mkdirs() }
        val photoFile = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            photoFile
        )
        pendingCaptureUriString = uri.toString()
        cameraLauncher.launch(uri)
    }

    AppDialog(
        title = stringResource(if (isEdit) R.string.edit_document else R.string.add_document),
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.document_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.document_description),
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            PhotoSection(
                existingPhotoPaths = existingPhotoPaths,
                removedExistingPaths = removedExistingPaths,
                newPhotoUris = newPhotoUris,
                isCameraAvailable = isCameraAvailable,
                onAddClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCameraClick = { launchCameraCapture() },
                onRemoveExisting = { removedExistingPaths.add(it) },
                onRemoveNew = { newPhotoUris.remove(it) }
            )
        },
        confirmButton = {
            AppButton(
                onClick = {
                    val resultDocument = (document ?: Document()).copy(
                        title = title.trim(),
                        description = description.trim(),
                        photoPaths = existingPhotoPaths.filter { it !in removedExistingPaths }
                    )
                    onConfirm(
                        resultDocument,
                        newPhotoUris.toList(),
                        removedExistingPaths.toList()
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(if (isEdit) R.string.save else R.string.create))
            }
        },
        dismissButton = { AppCancelButton(onClick = onDismiss) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoSection(
    existingPhotoPaths: List<String>,
    removedExistingPaths: List<String>,
    newPhotoUris: List<Uri>,
    isCameraAvailable: Boolean,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onRemoveExisting: (String) -> Unit,
    onRemoveNew: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visibleExisting = existingPhotoPaths.filter { it !in removedExistingPaths }
    val canAddMore = visibleExisting.size + newPhotoUris.size < MAX_PHOTOS
    val hasPhotos = visibleExisting.isNotEmpty() || newPhotoUris.isNotEmpty()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            visibleExisting.forEach { path ->
                PhotoThumbnail(
                    model = File(context.filesDir, path).toUri(),
                    onRemove = { onRemoveExisting(path) }
                )
            }
            newPhotoUris.forEach { uri ->
                PhotoThumbnail(
                    model = uri,
                    onRemove = { onRemoveNew(uri) }
                )
            }
            if (hasPhotos && canAddMore) {
                AddPhotoButton(onClick = onAddClick)
                if (isCameraAvailable) {
                    CameraPhotoButton(onClick = onCameraClick)
                }
            }
        }

        if (!hasPhotos) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppButton(
                    onClick = onAddClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.add_photo))
                }
                if (isCameraAvailable) {
                    CameraPhotoButton(onClick = onCameraClick)
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    model: Any,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(100.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.document_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.small)
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.delete),
                tint = AppTheme.colors.buttonContent,
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        AppTheme.colors.buttonContainer,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AddPhotoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PhotoActionButton(
        iconRes = R.drawable.ic_add,
        contentDescription = stringResource(R.string.add_photo),
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun CameraPhotoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PhotoActionButton(
        iconRes = R.drawable.ic_photo_camera,
        contentDescription = stringResource(R.string.take_photo),
        onClick = onClick,
        modifier = modifier
    )
}

// IconButton в Material3 сам применяет minimumInteractiveComponentSize (48dp)
// и круглый clip, поэтому размер и форма получаются не теми, что просишь.
// Обычный Box даёт ровно 40x40 с нужным скруглением.
@Composable
private fun PhotoActionButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(AppTheme.colors.buttonContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = AppTheme.colors.buttonContent
        )
    }
}
