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
import com.example.timemanager.presentation.components.AppFab
import com.example.timemanager.presentation.components.CategoryCard
import com.example.timemanager.presentation.components.CategoryInputDialog
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.ReorderableLazyColumn
import com.example.timemanager.presentation.components.SectionTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (Long) -> Unit,
    showBrandHeader: Boolean,
    onImportLists: () -> Unit,
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
        // РќРёР¶РЅРёР№ Р±Р°СЂ Р»РµР¶РёС‚ РїРѕРґ РїРµР№РґР¶РµСЂРѕРј РІ MainTabsScreen, РµРіРѕ РѕС‚СЃС‚СѓРї СѓР¶Рµ
        // СѓС‡С‚С‘РЅ. РЎС‚Р°С‚СѓСЃ-Р±Р°СЂРѕРј Р·Р°РЅРёРјР°РµС‚СЃСЏ С€Р°РїРєР°.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            // РџРµСЂРІС‹Р№ РїРѕ РЅР°СЃС‚СЂРѕР№РєРµ СЂР°Р·РґРµР» РїРѕР»СѓС‡Р°РµС‚ Р±СЂРµРЅРґ-С€Р°РїРєСѓ СЃ Р»РѕРіРѕС‚РёРїРѕРј Рё
            // РєРЅРѕРїРєРѕР№ РёРјРїРѕСЂС‚Р°; РѕСЃС‚Р°Р»СЊРЅС‹Рµ вЂ” С‚РѕР»СЊРєРѕ РЅР°Р·РІР°РЅРёРµ.
            SectionTopBar(
                title = stringResource(R.string.bottom_nav_categories),
                showBrandHeader = showBrandHeader,
                scrollBehavior = scrollBehavior,
                onImportLists = onImportLists
            )
        },
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
            text = stringResource(R.string.delete_category_confirm, category.name),
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                viewModel.onEvent(CategoriesEvent.OnDeleteCategory(category))
                categoryToDelete = null
            }
        )
    }
}
