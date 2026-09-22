package com.example.timemanager.presentation.components

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
import com.example.timemanager.R
import com.example.timemanager.presentation.navigation.Routes
import com.example.timemanager.presentation.theme.AppTheme

sealed class BottomNavItem(
    @DrawableRes val iconRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val route: String
) {
    data object Calendar : BottomNavItem(
        R.drawable.ic_calendar_month,
        R.string.bottom_nav_calendar,
        Routes.CALENDAR
    )

    data object Documents : BottomNavItem(
        R.drawable.ic_description,
        R.string.bottom_nav_documents,
        Routes.DOCUMENTS
    )

    data object Settings : BottomNavItem(
        R.drawable.ic_settings,
        R.string.bottom_nav_settings,
        Routes.SETTINGS
    )

    data object Categories : BottomNavItem(
        R.drawable.ic_list_alt,
        R.string.bottom_nav_categories,
        Routes.CATEGORIES
    )

    companion object {
        /**
         * Список намеренно геттер, а не `val`: как `val` в companion object он
         * инициализировался во время `<clinit>` самого [BottomNavItem], когда
         * синглтоны вложенных `data object` ещё не созданы, и список оказывался
         * с null-элементами (NPE в баре при запуске).
         */
        val items: List<BottomNavItem>
            get() = listOf(Categories, Calendar, Documents, Settings)

        /** Пункт бара для маршрута; `null` — если экран не является разделом. */
        fun fromRoute(route: String?): BottomNavItem? = when (route) {
            Routes.CATEGORIES -> Categories
            Routes.CALENDAR -> Calendar
            Routes.DOCUMENTS -> Documents
            Routes.SETTINGS -> Settings
            else -> null
        }
    }
}

/**
 * Единственный экземпляр нижнего навигационного бара на приложение.
 *
 * Рендерится вне [androidx.navigation.compose.NavHost], поэтому при переключении
 * разделов не пересоздаётся и не участвует в анимации перехода.
 */
@Composable
fun BottomNavBar(
    selectedItem: BottomNavItem?,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = AppTheme.colors.bottomNavContainer,
        modifier = modifier
    ) {
        BottomNavItem.items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.contentDescriptionRes)
                    )
                },
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppTheme.colors.bottomNavActiveIcon,
                    unselectedIconColor = AppTheme.colors.bottomNavInactiveIcon,
                    indicatorColor = AppTheme.colors.bottomNavIndicator
                )
            )
        }
    }
}
