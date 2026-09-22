package com.example.timemanager.presentation.screens.categories

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
import com.example.timemanager.domain.model.Category
import com.example.timemanager.presentation.components.AppBrandHeader
import com.example.timemanager.presentation.components.AppFab
import com.example.timemanager.presentation.components.CategoryCard
import com.example.timemanager.presentation.components.CategoryInputDialog
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.ReorderableLazyColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxSize(),
        // Нижний бар лежит под пейджером в MainTabsScreen, его отступ уже
        // учтён. Статус-баром занимается шапка.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { AppBrandHeader() },
        floatingActionButton = {
            AppFab(
                onClick = { showAddDialog = true },
                contentDescriptionRes = R.string.add_category
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_categories),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                ReorderableLazyColumn(
                    items = uiState.categories,
                    key = { it.id },
                    onReorder = { viewModel.onEvent(CategoriesEvent.OnReorderCategories(it)) },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) { category, _ ->
                    CategoryCard(
                        category = category,
                        onClick = { onCategoryClick(category.id) },
                        onEdit = { categoryToEdit = category },
                        onDelete = { categoryToDelete = category },
                        modifier = Modifier.fillMaxWidth()
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
            }
        )
    }

    categoryToEdit?.let { category ->
        CategoryInputDialog(
            category = category,
            onDismiss = { categoryToEdit = null },
            onConfirm = { name, color ->
                viewModel.onEvent(
                    CategoriesEvent.OnEditCategory(category.copy(name = name, color = color))
                )
                categoryToEdit = null
            }
        )
    }

    categoryToDelete?.let { category ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            text = "Удалить категорию \"${category.name}\"? Все задачи внутри неё также будут удалены.",
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                viewModel.onEvent(CategoriesEvent.OnDeleteCategory(category))
                categoryToDelete = null
            }
        )
    }
}
