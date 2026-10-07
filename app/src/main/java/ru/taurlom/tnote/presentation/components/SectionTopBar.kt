package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Шапка раздела главной панели: у первого по настройке раздела — бренд-шапка
 * с логотипом и названием; у остальных — обычный [AppTopBar] с названием.
 *
 * Вторичные действия раздела живут в меню ⋮ ([SectionMenu]), чтобы не плодить
 * иконки в шапке: «Загрузить список» (импорт `.tnote` — действие уровня
 * приложения, диалог подтверждения живёт в AppNavigation) есть всегда,
 * «Архив» — только у раздела «Списки» ([onArchiveClick]).
 *
 * [onSearchClick] открывает режим поиска раздела (см. [SearchTopBar]) —
 * кнопка 🔍 показывается только если колбэк передан. В календаре поиска пока
 * нет (кандидат на будущее: фильтр событий/заметок по тексту), поэтому он
 * колбэк не передаёт и иконку не получает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(
    title: String,
    showBrandHeader: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onImportLists: () -> Unit = {},
    onSearchClick: (() -> Unit)? = null,
    onArchiveClick: (() -> Unit)? = null,
) {
    if (showBrandHeader) {
        // Название раздела у первого не показываем: бренд-шапка — это
        // логотип и «T-Note», имя раздела своё у обычной шапки.
        AppBrandHeader(
            actions = {
                if (onSearchClick != null) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                            tint = AppTheme.colors.brandTitle,
                        )
                    }
                }
                SectionMenu(
                    onImportLists = onImportLists,
                    onArchiveClick = onArchiveClick,
                    iconTint = AppTheme.colors.brandTitle,
                )
            },
        )
    } else {
        AppTopBar(
            title = title,
            scrollBehavior = scrollBehavior,
            actions = {
                if (onSearchClick != null) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }
                SectionMenu(
                    onImportLists = onImportLists,
                    onArchiveClick = onArchiveClick,
                )
            },
        )
    }
}

/**
 * Меню ⋮ раздела: «Загрузить список» всегда, «Архив» — если передан колбэк.
 *
 * Кнопка и [DropdownMenu] обёрнуты в [Box]: попап позиционируется по узлу
 * меню, а узел нулевого размера без обёртки вставал в начало родительского
 * ряда — меню открывалось у левого края экрана вместо позиции под иконкой.
 *
 * Цвета — роли диалогов ([AppTheme.colors.dialogContainer]/`dialogContent`):
 * выпадающий список в палитре приложения приравнен к диалогам.
 */
@Composable
private fun SectionMenu(onImportLists: () -> Unit, onArchiveClick: (() -> Unit)?, iconTint: Color? = null) {
    var expanded by remember { mutableStateOf(false) }
    val itemColors = MenuDefaults.itemColors(
        textColor = AppTheme.colors.dialogContent,
        leadingIconColor = AppTheme.colors.dialogContent,
    )
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.more),
                tint = iconTint ?: AppTheme.colors.appBarContent,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AppTheme.colors.dialogContainer,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.load_list)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_download),
                        contentDescription = null,
                    )
                },
                colors = itemColors,
                onClick = {
                    expanded = false
                    onImportLists()
                },
            )
            if (onArchiveClick != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.archive_title)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_inventory_2),
                            contentDescription = null,
                        )
                    },
                    colors = itemColors,
                    onClick = {
                        expanded = false
                        onArchiveClick()
                    },
                )
            }
        }
    }
}
