package com.example.timemanager.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.timemanager.R
import com.example.timemanager.domain.model.Document
import com.example.timemanager.presentation.theme.DialogBackground
import com.example.timemanager.presentation.theme.DialogButtonBackground
import com.example.timemanager.presentation.theme.OnPrimary
import com.example.timemanager.presentation.theme.OnTertiary
import java.io.File

private const val MAX_PHOTOS = 4

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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PHOTOS)
    ) { uris ->
        val availableSlots = MAX_PHOTOS - existingPhotoPaths.size + removedExistingPaths.size - newPhotoUris.size
        newPhotoUris.addAll(uris.take(availableSlots.coerceAtLeast(0)))
    }

    val textFieldColors = outlinedDialogTextFieldColors()
    val buttonColors = dialogButtonColors()
    val isConfirmEnabled = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        title = {
            Text(
                stringResource(if (isEdit) R.string.edit_document else R.string.add_document),
                color = OnTertiary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.document_name)) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.document_description)) },
                    minLines = 3,
                    maxLines = 5,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                PhotoSection(
                    existingPhotoPaths = existingPhotoPaths,
                    removedExistingPaths = removedExistingPaths,
                    newPhotoUris = newPhotoUris,
                    onAddClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveExisting = { removedExistingPaths.add(it) },
                    onRemoveNew = { newPhotoUris.remove(it) },
                    buttonColors = buttonColors
                )
            }
        },
        confirmButton = {
            Button(
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
                enabled = isConfirmEnabled,
                shape = RoundedCornerShape(3.dp),
                colors = buttonColors
            ) {
                Text(stringResource(if (isEdit) R.string.save else R.string.create))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(3.dp),
                colors = buttonColors
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(3.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoSection(
    existingPhotoPaths: List<String>,
    removedExistingPaths: List<String>,
    newPhotoUris: List<Uri>,
    onAddClick: () -> Unit,
    onRemoveExisting: (String) -> Unit,
    onRemoveNew: (Uri) -> Unit,
    buttonColors: androidx.compose.material3.ButtonColors,
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
                AddPhotoButton(
                    onClick = onAddClick,
                    colors = buttonColors
                )
            }
        }

        if (!hasPhotos) {
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(3.dp),
                colors = buttonColors,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_photo))
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
                .clip(RoundedCornerShape(3.dp))
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.delete),
                tint = OnPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .background(DialogButtonBackground, shape = RoundedCornerShape(3.dp))
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AddPhotoButton(
    onClick: () -> Unit,
    colors: androidx.compose.material3.ButtonColors,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(3.dp),
        colors = colors,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.add_photo)
        )
    }
}


@Composable
private fun outlinedDialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OnTertiary,
    unfocusedTextColor = OnTertiary,
    focusedContainerColor = DialogBackground,
    unfocusedContainerColor = DialogBackground,
    disabledContainerColor = DialogBackground,
    focusedBorderColor = DialogButtonBackground,
    unfocusedBorderColor = DialogButtonBackground,
    focusedLabelColor = OnTertiary,
    unfocusedLabelColor = OnTertiary,
    cursorColor = OnTertiary,
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorLabelColor = MaterialTheme.colorScheme.error,
    errorCursorColor = MaterialTheme.colorScheme.error
)

@Composable
private fun dialogButtonColors() = ButtonDefaults.buttonColors(
    containerColor = DialogButtonBackground,
    contentColor = OnPrimary
)
