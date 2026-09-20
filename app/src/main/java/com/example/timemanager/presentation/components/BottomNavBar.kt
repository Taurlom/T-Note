package com.example.timemanager.presentation.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AddButtonBackground
import com.example.timemanager.presentation.theme.Background
import com.example.timemanager.presentation.theme.OnSecondary
import com.example.timemanager.presentation.theme.OnSurfaceVariant

sealed class BottomNavItem(
    val icon: ImageVector,
    @StringRes val contentDescriptionRes: Int
) {
    data object Calendar : BottomNavItem(
        Icons.Default.CalendarMonth,
        R.string.bottom_nav_calendar
    )

    data object Documents : BottomNavItem(
        Icons.Default.Description,
        R.string.bottom_nav_documents
    )

    data object Settings : BottomNavItem(
        Icons.Default.Settings,
        R.string.bottom_nav_settings
    )

    data object Categories : BottomNavItem(
        Icons.Default.ListAlt,
        R.string.bottom_nav_categories
    )
}

@Composable
fun BottomNavBar(
    selectedItem: BottomNavItem? = null,
    onItemSelected: (BottomNavItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Categories,
        BottomNavItem.Calendar,
        BottomNavItem.Documents,
        BottomNavItem.Settings
    )

    NavigationBar(
        containerColor = Background,
        modifier = modifier
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.contentDescriptionRes)
                    )
                },
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = OnSecondary,
                    unselectedIconColor = OnSurfaceVariant,
                    indicatorColor = AddButtonBackground
                )
            )
        }
    }
}
