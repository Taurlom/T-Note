package ru.taurlom.tnote.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Табы дизайн-системы: прокручиваемая строка вкладок с текстовыми
 * подписями. Когда вкладки не влезают в ширину экрана, строка листается
 * горизонтально. Стиль — из компонентных ролей темы: выбранный таб —
 * акцентным цветом с индикатором-полоской, остальные приглушённо,
 * без разделительной линии снизу.
 */
@Composable
fun AppTabs(titles: List<String>, selectedTab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        modifier = modifier,
        containerColor = Color.Transparent,
        edgePadding = 0.dp,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = AppTheme.colors.tabIndicator,
            )
        },
        divider = {},
    ) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedTab,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (index == selectedTab) {
                            AppTheme.colors.tabSelectedContent
                        } else {
                            AppTheme.colors.tabContent
                        },
                    )
                },
            )
        }
    }
}
