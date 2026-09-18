package com.example.timemanager.presentation.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
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
import com.example.timemanager.R
import com.example.timemanager.domain.model.Category
import com.example.timemanager.presentation.components.BottomNavBar
import com.example.timemanager.presentation.components.BottomNavItem
import com.example.timemanager.presentation.components.CategoryCard
import com.example.timemanager.presentation.components.CategoryInputDialog
import com.example.timemanager.presentation.components.ConfirmDeleteDialog
import com.example.timemanager.presentation.components.ReorderableLazyColumn
import com.example.timemanager.presentation.theme.AddButtonBackground
import com.example.timemanager.presentation.theme.AppBarBackground
import com.example.timemanager.presentation.theme.OnSecondary
import com.example.timemanager.presentation.theme.OnTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (Long) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
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
                    contentDescription = stringResource(R.string.add_category),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                selectedItem = null,
                onItemSelected = { item ->
                    when (item) {
                        BottomNavItem.Calendar -> onNavigateToCalendar()
                        BottomNavItem.Documents -> onNavigateToDocuments()
                        BottomNavItem.Settings -> onNavigateToSettings()
                    }
                }
            )
        }
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
