package com.example.timemanager.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Шапка раздела главной панели: у первого по настройке раздела — бренд-шапка
 * с логотипом, названием раздела и кнопкой импорта списка; у остальных —
 * обычный [AppTopBar] только с названием.
 *
 * Импорт `.tnote` — действие уровня приложения (диалог подтверждения живёт в
 * AppNavigation), поэтому кнопка доступна, каким бы первым раздел ни оказался.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(
    title: String,
    showBrandHeader: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onImportLists: () -> Unit = {}
) {
    if (showBrandHeader) {
        AppBrandHeader(
            title = title,
            actions = {
                IconButton(onClick = onImportLists) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_download),
                        contentDescription = stringResource(R.string.import_list),
                        tint = AppTheme.colors.brandTitle
                    )
                }
            }
        )
    } else {
        AppTopBar(
            title = title,
            scrollBehavior = scrollBehavior
        )
    }
}
