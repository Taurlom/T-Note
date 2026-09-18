package com.example.timemanager.presentation.screens.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.timemanager.R
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.PhotoGalleryDialog
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.OnTertiary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    onBackClick: () -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val document = uiState.document
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var galleryIndex by remember { mutableStateOf<Int?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.title ?: stringResource(R.string.document_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (document != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarBackground,
                    titleContentColor = OnTertiary,
                    navigationIconContentColor = OnTertiary,
                    actionIconContentColor = OnTertiary
                )
            )
        }
    ) { padding ->
        if (document == null) {
            if (!uiState.isLoading) {
                Text(
                    text = stringResource(R.string.document_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }
            return@Scaffold
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.headlineSmall
            )

            if (document.description.isNotBlank()) {
                Text(
                    text = document.description,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Text(
                text = stringResource(R.string.created_at, document.createdAt.formatDate()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (document.photoPaths.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(document.photoPaths) { index, path ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(context.filesDir, path).toUri())
                                .setParameter("version", photoVersion)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.document_photo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { galleryIndex = index }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    galleryIndex?.let { index ->
        PhotoGalleryDialog(
            photoPaths = document?.photoPaths ?: emptyList(),
            initialIndex = index,
            onDismiss = {
                galleryIndex = null
                photoVersion++
            }
        )
    }

    if (showDeleteDialog && document != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = "Удалить документ \"${document.title}\"?",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deleteDocument(onDeleted = onBackClick)
            }
        )
    }
}

private fun Long.formatDate(): String {
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(this)
}
