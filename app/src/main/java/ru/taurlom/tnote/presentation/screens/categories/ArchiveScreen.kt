package ru.taurlom.tnote.presentation.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.presentation.components.AppTopBar
import ru.taurlom.tnote.presentation.components.ArchiveCategoryCard
import ru.taurlom.tnote.presentation.components.ConfirmDeleteDialog
import ru.taurlom.tnote.presentation.components.SearchTopBar
import ru.taurlom.tnote.presentation.components.rememberSearchState
import ru.taurlom.tnote.presentation.components.searchMatch

/**
 * Экран архива списков: списки, скрытые из основного перечня корзиной.
 * Отсюда можно вернуть список (в конец основного списка) или удалить
 * навсегда вместе с задачами — единственное место необратимого удаления.
 *
 * Поиск — тот же общий [SearchTopBar], что и в разделах; перетаскивания
 * нет (архив отсортирован по имени).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(onBackClick: () -> Unit, viewModel: ArchiveViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    val search = rememberSearchState()
    val query = search.query
    val visibleCategories = remember(uiState.categories, query) {
        uiState.categories.filter { searchMatch(query, it.name) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (search.active) {
                SearchTopBar(
                    query = query,
                    onQueryChange = search::onQueryChange,
                    onClose = search::close,
                    placeholder = stringResource(R.string.search_categories_hint),
                )
            } else {
                AppTopBar(
                    title = stringResource(R.string.archive_title),
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = search::open) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (visibleCategories.isEmpty()) {
                Text(
                    text = stringResource(
                        if (query.isNotBlank()) R.string.no_search_results else R.string.archive_empty,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visibleCategories, key = { it.id }) { category ->
                        ArchiveCategoryCard(
                            category = category,
                            onRestore = { viewModel.onEvent(ArchiveEvent.OnRestore(category)) },
                            onDelete = { categoryToDelete = category },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    categoryToDelete?.let { category ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_forever),
            text = stringResource(R.string.delete_category_confirm, category.name),
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                viewModel.onEvent(ArchiveEvent.OnDeleteForever(category))
                categoryToDelete = null
            },
        )
    }
}
