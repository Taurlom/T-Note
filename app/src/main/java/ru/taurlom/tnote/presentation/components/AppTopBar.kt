package ru.taurlom.tnote.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/** Верхняя панель раздела — единые цвета для всех экранов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppTheme.colors.appBarContainer,
            // При прокрутке под фиксированной панелью контент «подъезжает»
            // к ней — цвет переключается на scrolled, если экрану передан
            // scrollBehavior (см. pinnedScrollBehavior на экранах).
            scrolledContainerColor = AppTheme.colors.appBarScrolledContainer,
            titleContentColor = AppTheme.colors.appBarContent,
            navigationIconContentColor = AppTheme.colors.appBarContent,
            actionIconContentColor = AppTheme.colors.appBarContent,
        ),
    )
}

// ── Previews ──

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "С заголовком")
@Composable
private fun AppTopBarTitlePreview() {
    TNoteTheme {
        AppTopBar(title = "Мои списки")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "С кнопкой назад")
@Composable
private fun AppTopBarWithBackPreview() {
    TNoteTheme {
        AppTopBar(
            title = "Задачи",
            navigationIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Назад",
                )
            },
        )
    }
}
