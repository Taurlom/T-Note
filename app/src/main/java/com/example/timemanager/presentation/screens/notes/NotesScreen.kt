package com.example.timemanager.presentation.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.timemanager.domain.model.Note
import com.example.timemanager.presentation.components.AppFab
import com.example.timemanager.presentation.components.AppTopBar
import com.example.timemanager.presentation.components.SectionTopBar
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.NoteItem
import com.example.timemanager.presentation.components.ReorderableLazyColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onNoteClick: (Long) -> Unit,
    showBrandHeader: Boolean,
    onImportLists: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        // Нижний бар лежит под пейджером в MainTabsScreen — его не учитываем.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            SectionTopBar(
                title = stringResource(R.string.notes_title),
                showBrandHeader = showBrandHeader,
                scrollBehavior = scrollBehavior,
                onImportLists = onImportLists
            )
        },
        floatingActionButton = {
            // Новая заметка — тот же экран деталей в режиме редактирования
            // (id 0), длинный текст в диалоге тесно.
            AppFab(
                onClick = { onNoteClick(NEW_NOTE_ID) },
                contentDescriptionRes = R.string.add_note
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.notes.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_notes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ReorderableLazyColumn(
                    items = uiState.notes,
                    key = { it.id },
                    onReorder = { viewModel.onEvent(NotesEvent.OnReorderNotes(it)) },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) { note, _ ->
                    NoteItem(
                        note = note,
                        onClick = { onNoteClick(note.id) },
                        onDelete = { noteToDelete = note },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    noteToDelete?.let { note ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_note_confirm, note.title),
            onDismiss = { noteToDelete = null },
            onConfirm = {
                viewModel.onEvent(NotesEvent.OnDeleteNote(note))
                noteToDelete = null
            }
        )
    }
}

/** Маркер «новой заметки» в маршруте деталей: в базе id начинаются с 1. */
const val NEW_NOTE_ID = 0L
