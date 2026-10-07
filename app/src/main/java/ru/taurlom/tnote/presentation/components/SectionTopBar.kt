package ru.taurlom.tnote.presentation.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * с логотипом, названием раздела и кнопкой импорта списка; у остальных —
 * обычный [AppTopBar] только с названием.
 *
 * Импорт `.tnote` — действие уровня приложения (диалог подтверждения живёт в
 * AppNavigation), поэтому кнопка доступна, каким бы первым раздел ни оказался.
 *
 * [onSearchClick] открывает режим поиска раздела (см. [SearchTopBar]) —
 * кнопка 🔍 показывается только если колбэк передан. В календаре поиска пока
 * нет (кандидат на будущее: фильтр событий/заметок по тексту), поэтому он
 * колбэк не передаёт и иконку не получает.
 *
 * [onArchiveClick] — то же правило: передаёт только раздел «Списки», в шапке
 * появляется меню ⋮ с пунктом «Архив». Вторичные действия раздела живут в
 * меню, чтобы не плодить иконки в шапке.
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
                IconButton(onClick = onImportLists) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_download),
                        contentDescription = stringResource(R.string.import_list),
                        tint = AppTheme.colors.brandTitle,
                    )
                }
                if (onArchiveClick != null) {
                    ArchiveMenu(onArchiveClick = onArchiveClick, iconTint = AppTheme.colors.brandTitle)
                }
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
                if (onArchiveClick != null) {
                    ArchiveMenu(onArchiveClick = onArchiveClick)
                }
            },
        )
    }
}

/** Кнопка ⋮ с выпадающим меню раздела (сейчас пункт один — «Архив»). */
@Composable
private fun ArchiveMenu(onArchiveClick: () -> Unit, iconTint: Color? = null) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.more),
            tint = iconTint ?: AppTheme.colors.appBarContent,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.archive_title)) },
            onClick = {
                expanded = false
                onArchiveClick()
            },
        )
    }
}
