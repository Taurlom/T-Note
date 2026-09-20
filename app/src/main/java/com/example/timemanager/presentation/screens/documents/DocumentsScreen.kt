package com.example.timemanager.presentation.screens.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.domain.model.Document
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.DocumentInputDialog
import com.example.timemanager.presentation.components.DocumentItem
import com.example.timemanager.presentation.theme.AddButtonBackground
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.OnSecondary
import com.example.timemanager.presentation.theme.OnTertiary


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onDocumentClick: (Long) -> Unit,
    viewModel: DocumentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showAddDialog by remember { mutableStateOf(false) }
    var documentToEdit by remember { mutableStateOf<Document?>(null) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        // Нижний бар рендерится над NavHost в AppNavigation.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.documents_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarBackground,
                    titleContentColor = OnTertiary,
                    navigationIconContentColor = OnTertiary,
                    actionIconContentColor = OnTertiary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(3.dp),
                containerColor = AddButtonBackground,
                contentColor = OnSecondary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_document),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.documents.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_documents),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.documents,
                        key = { it.id }
                    ) { document ->
                        DocumentItem(
                            document = document,
                            onClick = { onDocumentClick(document.id) },
                            onEdit = { documentToEdit = document },
                            onDelete = { documentToDelete = document },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        DocumentInputDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { document, photoUris, _ ->
                viewModel.onEvent(
                    DocumentsEvent.OnAddDocument(document, photoUris)
                )
                showAddDialog = false
            }
        )
    }

    documentToEdit?.let { document ->
        DocumentInputDialog(
            document = document,
            onDismiss = { documentToEdit = null },
            onConfirm = { updatedDocument, newPhotoUris, removedPhotoPaths ->
                viewModel.onEvent(
                    DocumentsEvent.OnEditDocument(
                        document = updatedDocument,
                        newPhotoUris = newPhotoUris,
                        removedPhotoPaths = removedPhotoPaths
                    )
                )
                documentToEdit = null
            }
        )
    }

    documentToDelete?.let { document ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = "Удалить документ \"${document.title}\"?",
            onDismiss = { documentToDelete = null },
            onConfirm = {
                viewModel.onEvent(DocumentsEvent.OnDeleteDocument(document))
                documentToDelete = null
            }
        )
    }
}
