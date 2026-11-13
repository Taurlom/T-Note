package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Шапка раздела главной панели: у первого по настройке раздела — бренд-шапка
 * с логотипом и названием; у остальных — обычный [AppTopBar] с названием.
 *
 * Вторичные действия раздела живут в меню ⋮ ([SectionMenu]), чтобы не плодить
 * иконки в шапке: «Загрузить список» (импорт `.tnote`) — только у раздела
 * «Списки» ([onImportLists], диалог подтверждения живёт в AppNavigation),
 * «Архив» — тоже только у «Списков» ([onArchiveClick]). Если не передано
 * ни одного действия, меню ⋮ не показывается вовсе.
 *
 * [onSearchClick] открывает режим поиска раздела (см. [SearchTopBar]) —
 * кнопка 🔍 показывается только если колбэк передан. В календаре поиска пока
 * нет (кандидат на будущее: фильтр событий/заметок по тексту), поэтому он
 * колбэк не передаёт и иконку не получает.
 *
 * [onSyncClick] — ручная синхронизация CalDAV из шапки (иконка ⇄): календарь
 * передаёт её, когда подписка подключена; остальным разделам кнопка не нужна.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(
    title: String,
    showBrandHeader: Boolean,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onSyncClick: (() -> Unit)? = null,
    onImportLists: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onArchiveClick: (() -> Unit)? = null,
) {
    val menuActions: @Composable () -> Unit = {
        if (onImportLists != null || onArchiveClick != null) {
            SectionMenu(
                onImportLists = onImportLists,
                onArchiveClick = onArchiveClick,
                iconTint = if (showBrandHeader) AppTheme.colors.brandTitle else null,
            )
        }
    }
    if (showBrandHeader) {
        // Название раздела у первого не показываем: бренд-шапка — это
        // логотип и «T-Note», имя раздела своё у обычной шапки.
        AppBrandHeader(
            actions = {
                if (onSyncClick != null) {
                    IconButton(onClick = onSyncClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = stringResource(R.string.sync_refresh),
                            tint = AppTheme.colors.brandTitle,
                        )
                    }
                }
                if (onSearchClick != null) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                            tint = AppTheme.colors.brandTitle,
                        )
                    }
                }
                menuActions()
            },
        )
    } else {
        AppTopBar(
            title = title,
            scrollBehavior = scrollBehavior,
            actions = {
                if (onSyncClick != null) {
                    IconButton(onClick = onSyncClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = stringResource(R.string.sync_refresh),
                        )
                    }
                }
                if (onSearchClick != null) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                }
                menuActions()
            },
        )
    }
}

/**
 * Меню ⋮ раздела: «Загрузить список» и «Архив» — по переданным колбэкам
 * (показывается только то, на что есть действие).
 *
 * Кнопка и [DropdownMenu] обёрнуты в [Box]: попап позиционируется по узлу
 * меню, а узел нулевого размера без обёртки вставал в начало родительского
 * ряда — меню открывалось у левого края экрана вместо позиции под иконкой.
 *
 * Цвета — роли приложения: пункты — [AppTheme.colors.menuItemContent] (в тёмной
 * теме — акцент Gold), контейнер — как у диалогов ([AppTheme.colors.dialogContainer]),
 * контур — [AppTheme.colors.menuBorder], отделяющий меню от фона.
 */
@Composable
private fun SectionMenu(onImportLists: (() -> Unit)?, onArchiveClick: (() -> Unit)?, iconTint: Color? = null) {
    var expanded by remember { mutableStateOf(false) }
    val itemColors = MenuDefaults.itemColors(
        textColor = AppTheme.colors.menuItemContent,
        leadingIconColor = AppTheme.colors.menuItemContent,
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
            border = BorderStroke(1.dp, AppTheme.colors.menuBorder),
        ) {
            if (onImportLists != null) {
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
            }
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
