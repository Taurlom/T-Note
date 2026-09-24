package com.example.timemanager.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Шапка главного экрана: логотип и название приложения на фоне экрана
 * (в отличие от [AppTopBar] — без панели-контейнера).
 *
 * Цвета — ролей темы [AppTheme.colors.brandLogo] и `brandTitle`, поэтому
 * в каждой теме шапка своя.
 */
@Composable
fun AppBrandHeader(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.logo_vector),
            contentDescription = null,
            tint = AppTheme.colors.brandLogo,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.brandTitle,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}
