package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.TNoteTheme

/**
 * Бейдж тега заметки: слово с `#`, палитра чипов дизайн-системы.
 * Недологгирован — чип только отображает метку.
 */
@Composable
fun TagChip(tag: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.chipContainer,
        contentColor = colors.chipContent,
    ) {
        Text(
            text = "#$tag",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Бейджи тегов заметки в просмотре: переносятся по строкам (FlowRow),
 * пустой список — ничего (вызывающая сторона может не ветвить).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagBadges(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag -> TagChip(tag) }
    }
}

// ── Previews ──

@Preview(showBackground = true, name = "Чип тега")
@Composable
private fun TagChipPreview() {
    TNoteTheme {
        TagChip(tag = "рецепт", modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, name = "Бейджи тегов")
@Composable
private fun TagBadgesPreview() {
    TNoteTheme {
        TagBadges(
            tags = listOf("рецепт", "кулинария", "на выходные"),
            modifier = Modifier.padding(16.dp),
        )
    }
}
