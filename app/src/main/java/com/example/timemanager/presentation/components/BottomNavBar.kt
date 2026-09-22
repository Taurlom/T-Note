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
import com.example.timemanager.presentation.theme.AppTheme

sealed class BottomNavItem(
    @DrawableRes val iconRes: Int,
    @StringRes val contentDescriptionRes: Int
) {
    data object Calendar : BottomNavItem(
        R.drawable.ic_calendar_month,
        R.string.bottom_nav_calendar
    )

    data object Documents : BottomNavItem(
        R.drawable.ic_description,
        R.string.bottom_nav_documents
    )

    data object Settings : BottomNavItem(
        R.drawable.ic_settings,
        R.string.bottom_nav_settings
    )

    data object Categories : BottomNavItem(
        R.drawable.ic_list_alt,
        R.string.bottom_nav_categories
    )

    companion object {
        /**
         * Список намеренно геттер, а не `val`: как `val` в companion object он
         * инициализировался во время `<clinit>` самого [BottomNavItem], когда
         * синглтоны вложенных `data object` ещё не созданы, и список оказывался
         * с null-элементами (NPE в баре при запуске).
         * Порядок списка = порядок страниц пейджера разделов.
         */
        val items: List<BottomNavItem>
            get() = listOf(Categories, Calendar, Documents, Settings)
    }
}

/**
 * Нижняя навигационная панель разделов.
 *
 * Живёт внутри экрана разделов ([com.example.timemanager.presentation.navigation.Routes.MAIN]),
 * поэтому на drill-down экранах (список задач, документ) её просто не видно,
 * а сама панель не пересоздаётся при переключении страниц пейджера.
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
