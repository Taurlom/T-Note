package com.example.timemanager.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Квадратная плитка фотографии документа: загрузка Coil, кадрирование,
 * скругление из темы. Переиспользуется в галерее документа (крупная, клик
 * открывает просмотр) и в диалоге редактирования (мелкая, с кнопкой удаления
 * поверх — см. [RemovablePhotoTile]).
 *
 * @param model источник изображения (например, `Uri` файла фотографии).
 * @param version меняйте значение, чтобы заставить плитку перезагрузиться
 *        после редактирования фото (crop/поворот) — передаётся Coil как ключ.
 * @param onClick клик по плитке (галерея); без него плитка некликабельна.
 */
@Composable
fun PhotoTile(
    model: Any,
    modifier: Modifier = Modifier,
    version: Any? = null,
    onClick: (() -> Unit)? = null
) {
    val requestBuilder = ImageRequest.Builder(LocalContext.current)
        .data(model)
        .crossfade(true)
    if (version != null) {
        requestBuilder.setParameter("version", version)
    }
    AsyncImage(
        model = requestBuilder.build(),
        contentDescription = stringResource(R.string.document_photo),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/**
 * Плитка фото для диалога редактирования: мелкий квадрат с крестиком удаления
 * в углу поверх изображения.
 */
@Composable
fun RemovablePhotoTile(
    model: Any,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(100.dp)) {
        PhotoTile(
            model = model,
            modifier = Modifier.matchParentSize()
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.delete),
                tint = AppTheme.colors.buttonContent,
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        AppTheme.colors.buttonContainer,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(4.dp)
            )
        }
    }
}
