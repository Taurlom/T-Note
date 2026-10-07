package ru.taurlom.tnote.presentation.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.presentation.components.AppFab
import ru.taurlom.tnote.presentation.components.CategoryCard
import ru.taurlom.tnote.presentation.components.CategoryInputDialog
import ru.taurlom.tnote.presentation.components.ReorderableLazyColumn
import ru.taurlom.tnote.presentation.components.SearchTopBar
import ru.taurlom.tnote.presentation.components.SectionTopBar
import ru.taurlom.tnote.presentation.components.rememberSearchState
import ru.taurlom.tnote.presentation.components.searchMatch
import ru.taurlom.tnote.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (Long) -> Unit,
    showBrandHeader: Boolean,
    onImportLists: () -> Unit,
    onArchiveClick: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }

    // Снекбар «перемещён в архив / Отменить»: архивация обратима, поэтому
    // без диалога подтверждения — Undo возвращает список на место.
    val snackbarHostState = remember { SnackbarHostState() }
    val onArchiveCategory: (Category) -> Unit = { category ->
        scope.launch {
            viewModel.onEvent(CategoriesEvent.OnArchiveCategory(category))
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.category_archived, category.name),
                actionLabel = context.getString(R.string.undo),
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onEvent(CategoriesEvent.OnRestoreCategory(category))
            }
        }
    }

    val search = rememberSearchState()
    val query = search.query
    // Поиск по названию списка: задачи внутри категорий не матчим —
    // результат показывается карточкой списка, искать внутри него
    // удобнее своим поиском на экране списка.
    val visibleCategories = remember(uiState.categories, query) {
        uiState.categories.filter { searchMatch(query, it.name) }
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        // Нижний бар лежит под пейджером в MainTabsScreen, его отступ уже
        // учтён. Статус-баром занимается шапка.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                // M3 держит снекбар с действием ~10 секунд — для «Отменить»
                // это вечность. Гасим сами через 2,5 с: dismiss() даёт
                // showSnackbar результат Dismissed, undo не срабатывает.
                LaunchedEffect(data) {
                    delay(2_500)
                    data.dismiss()
                }
                Snackbar(
                    containerColor = AppTheme.colors.snackbarContainer,
                    contentColor = AppTheme.colors.snackbarContent,
                    actionContentColor = AppTheme.colors.snackbarAction,
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            // TextButton красит текст своим primary мимо
                            // палитры снекбара — цвет действия задаём явно.
                            TextButton(
                                onClick = { data.performAction() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = AppTheme.colors.snackbarAction,
                                ),
                            ) {
                                Text(label)
                            }
                        }
                    },
                ) {
                    Text(data.visuals.message)
                }
            }
        },
        topBar = {
            if (search.active) {
                SearchTopBar(
                    query = query,
                    onQueryChange = search::onQueryChange,
                    onClose = search::close,
                    placeholder = stringResource(R.string.search_categories_hint),
                )
            } else {
                // Первый по настройке раздел получает бренд-шапку с логотипом и
                // кнопкой импорта; остальные — только название.
                SectionTopBar(
                    title = stringResource(R.string.bottom_nav_categories),
                    showBrandHeader = showBrandHeader,
                    scrollBehavior = scrollBehavior,
                    onImportLists = onImportLists,
                    onSearchClick = search::open,
                    onArchiveClick = onArchiveClick,
                )
            }
        },
        floatingActionButton = {
            // В режиме поиска новый список не нужен — FAB мешает результатам.
            if (!search.active) {
                AppFab(
                    onClick = { showAddDialog = true },
                    contentDescriptionRes = R.string.add_category,
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
                        if (query.isNotBlank()) R.string.no_search_results else R.string.no_categories,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                ReorderableLazyColumn(
                    items = visibleCategories,
                    key = { it.id },
                    onReorder = { viewModel.onEvent(CategoriesEvent.OnReorderCategories(it)) },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    // Drag-and-drop только в полном списке: перетаскивание
                    // отфильтрованного сломало бы позиции остальных списков.
                    reorderEnabled = !search.active,
                    modifier = Modifier.fillMaxSize(),
                ) { category, _ ->
                    CategoryCard(
                        category = category,
                        onClick = { onCategoryClick(category.id) },
                        onEdit = { categoryToEdit = category },
                        // Корзина теперь архивирует: удаление навсегда
                        // переехало на экран архива (меню ⋮).
                        onDelete = { onArchiveCategory(category) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryInputDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, color ->
                viewModel.onEvent(CategoriesEvent.OnAddCategory(name, color))
                showAddDialog = false
            },
        )
    }

    categoryToEdit?.let { category ->
        CategoryInputDialog(
            category = category,
            onDismiss = { categoryToEdit = null },
            onConfirm = { name, color ->
                viewModel.onEvent(
                    CategoriesEvent.OnEditCategory(category.copy(name = name, color = color)),
                )
                categoryToEdit = null
            },
        )
    }
}
