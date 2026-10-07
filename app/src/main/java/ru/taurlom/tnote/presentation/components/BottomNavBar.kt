package ru.taurlom.tnote.presentation.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

sealed class BottomNavItem(
    /** Стабильный идентификатор для настроек видимости/порядка. */
    val id: String,
    @DrawableRes val iconRes: Int,
    @StringRes val contentDescriptionRes: Int,
) {
    data object Calendar : BottomNavItem(
        "calendar",
        R.drawable.ic_calendar_month,
        R.string.bottom_nav_calendar,
    )

    data object Documents : BottomNavItem(
        "documents",
        R.drawable.ic_article_person,
        R.string.bottom_nav_documents,
    )

    data object Notes : BottomNavItem(
        "notes",
        R.drawable.ic_contract_edit,
        R.string.bottom_nav_notes,
    )

    data object Settings : BottomNavItem(
        "settings",
        R.drawable.ic_settings,
        R.string.bottom_nav_settings,
    )

    data object Categories : BottomNavItem(
        "categories",
        R.drawable.ic_list_alt,
        R.string.bottom_nav_categories,
    )

    companion object {
        /**
         * Разделы, доступные для скрытия и перетаскивания, в порядке
         * по умолчанию. «Настройки» не участвуют: без них приложение
         * теряет доступ к себе.
         */
        val toggleable: List<BottomNavItem>
            get() = listOf(Categories, Notes, Calendar, Documents)

        /**
         * Порядок страниц пейджера из сохранённого списка id: пустой или
         * null — порядок по умолчанию; незнакомые id (после отката версии)
         * игнорируются; «Настройки» всегда в конце. Хранится только видимая
         * часть — её порядок и есть порядок в баре.
         */
        fun itemsFor(visibleIds: List<String>?): List<BottomNavItem> {
            val known = toggleable.associateBy { it.id }
            val visible = if (visibleIds.isNullOrEmpty()) {
                toggleable
            } else {
                visibleIds.mapNotNull { known[it] }
            }
            // Скрыть последний раздел нельзя, но повреждённый список не должен
            // оставить бар без страниц.
            return (if (visible.isEmpty()) toggleable else visible) + Settings
        }
    }
}

/**
 * Нижняя навигационная панель разделов.
 *
 * Живёт внутри экрана разделов ([ru.taurlom.tnote.presentation.navigation.Routes.MAIN]),
 * поэтому на drill-down экранах (список задач, документ) её просто не видно,
 * а сама панель не пересоздаётся при переключении страниц пейджера.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedItem: BottomNavItem?,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = AppTheme.colors.bottomNavContainer,
        modifier = modifier,
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.contentDescriptionRes),
                    )
                },
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppTheme.colors.bottomNavActiveIcon,
                    unselectedIconColor = AppTheme.colors.bottomNavInactiveIcon,
                    indicatorColor = AppTheme.colors.bottomNavIndicator,
                ),
            )
        }
    }
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun BottomNavBarPreview() {
    TNoteTheme {
        BottomNavBar(
            items = BottomNavItem.toggleable + BottomNavItem.Settings,
            selectedItem = BottomNavItem.Categories,
            onItemSelected = {},
        )
    }
}
