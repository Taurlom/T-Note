package ru.taurlom.tnote.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.StorageUsage
import ru.taurlom.tnote.presentation.theme.AppTheme
import java.util.Locale

/**
 * Блок «Использование хранилища» (вкладка «Данные»): суммарный объём файлов
 * приложения, полоса-детализация и строки по группам (фото, документы,
 * база, прочее). Цвета групп — роли темы [AppTheme.colors] (storage*),
 * поэтому полоса и маркеры подстраиваются под тему сами.
 */
@Composable
internal fun StorageUsageCard(usage: StorageUsage?, modifier: Modifier = Modifier) {
    if (usage == null) return // ещё не посчитано — блок появится после чтения диска

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = formatBytes(usage.totalBytes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.storage_subtitle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))
        UsageBar(usage)
        Spacer(modifier = Modifier.height(8.dp))
        Legend(usage)

        Spacer(modifier = Modifier.height(8.dp))
        UsageRow(
            color = AppTheme.colors.storagePhotos,
            iconRes = R.drawable.ic_photo_camera,
            title = stringResource(R.string.storage_photos),
            subtitle = pluralStringResource(R.plurals.storage_files, usage.photos.files, usage.photos.files),
            bytes = usage.photos.bytes,
            total = usage.totalBytes,
        )
        UsageRow(
            color = AppTheme.colors.storageDocuments,
            iconRes = R.drawable.ic_description,
            title = stringResource(R.string.storage_documents),
            subtitle = pluralStringResource(R.plurals.storage_files, usage.documents.files, usage.documents.files),
            bytes = usage.documents.bytes,
            total = usage.totalBytes,
        )
        UsageRow(
            color = AppTheme.colors.storageDatabase,
            iconRes = R.drawable.ic_database,
            title = stringResource(R.string.storage_database),
            subtitle = stringResource(R.string.storage_db_subtitle),
            bytes = usage.database.bytes,
            total = usage.totalBytes,
        )
        UsageRow(
            color = AppTheme.colors.storageOther,
            iconRes = R.drawable.ic_folder,
            title = stringResource(R.string.storage_other),
            subtitle = stringResource(R.string.storage_other_subtitle),
            bytes = usage.other.bytes,
            total = usage.totalBytes,
        )
    }
}

/** Полоса-детализация: доля каждой группы пропорциональна её объёму. */
@Composable
private fun UsageBar(usage: StorageUsage, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val segments = listOf(
        colors.storagePhotos to usage.photos.bytes,
        colors.storageDocuments to usage.documents.bytes,
        colors.storageDatabase to usage.database.bytes,
        colors.storageOther to usage.other.bytes,
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track),
    ) {
        if (usage.totalBytes == 0L) return@Row
        segments.forEach { (color, bytes) ->
            if (bytes > 0) {
                Box(
                    modifier = Modifier
                        .weight(bytes.toFloat())
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(color),
                )
            }
        }
    }
}

/** Маркеры-точки с подписями групп; переносятся на вторую строку на узких экранах. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(usage: StorageUsage, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(AppTheme.colors.storagePhotos, stringResource(R.string.storage_photos))
        LegendItem(AppTheme.colors.storageDocuments, stringResource(R.string.storage_documents))
        LegendItem(AppTheme.colors.storageDatabase, stringResource(R.string.storage_database))
        LegendItem(AppTheme.colors.storageOther, stringResource(R.string.storage_other))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Строка группы: иконка-чип, название с подписью, справа объём и доля. */
@Composable
private fun UsageRow(
    color: Color,
    iconRes: Int,
    title: String,
    subtitle: String,
    bytes: Long,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatBytes(bytes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatPercent(bytes, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Форматирование ──

/**
 * Человекочитаемый объём («4,2 МБ»): до килобайта — байты, дальше —
 * KB/MB/GB. Разделитель и цифры — по локали интерфейса.
 */
@Composable
private fun formatBytes(bytes: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        bytes <= 0 -> String.format(locale, "%d %s", 0, stringResource(R.string.storage_unit_kb))
        bytes < KB -> String.format(locale, "%d %s", bytes, stringResource(R.string.storage_unit_b))
        bytes < MB -> String.format(locale, "%.0f %s", kb, stringResource(R.string.storage_unit_kb))
        bytes < GB -> String.format(locale, "%.1f %s", mb, stringResource(R.string.storage_unit_mb))
        else -> String.format(locale, "%.2f %s", gb, stringResource(R.string.storage_unit_gb))
    }
}

/** Доля группы в процентах («81,5 %»); пустое хранилище — «0 %». */
@Composable
private fun formatPercent(bytes: Long, total: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val percent = if (total > 0) bytes * 100.0 / total else 0.0
    return String.format(locale, "%.1f %%", percent)
}

private const val KB = 1024L
private const val MB = 1024L * 1024
private const val GB = 1024L * 1024 * 1024
