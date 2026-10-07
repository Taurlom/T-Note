package ru.taurlom.tnote.presentation.screens.notes

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.components.PhotoTile
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.util.MarkdownEditing

/**
 * Миниатюра фото заметки с крестиком удаления из черновика.
 */
@Composable
internal fun NotePhotoThumb(
    model: Any,
    onRemove: () -> Unit,
    version: Any? = null
) {
    Box(modifier = Modifier.size(84.dp)) {
        PhotoTile(
            model = model,
            version = version,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.delete),
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * Панель вставки markdown-меток: жирный, заголовок, список, ссылка.
 * Операции — чистые функции [MarkdownEditing] над текстом и выделением поля.
 */
@Composable
internal fun FormattingToolbar(
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val linkPlaceholder = stringResource(R.string.markdown_link_placeholder)

    fun apply(op: (text: String, selection: TextRange) -> Pair<String, TextRange>) {
        val (text, selection) = op(content.text, content.selection)
        onContentChange(TextFieldValue(text, selection))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        FormattingButton(R.drawable.ic_format_bold, R.string.note_format_bold) {
            apply { text, sel -> MarkdownEditing.toggleBold(text, sel) }
        }
        FormattingButton(R.drawable.ic_title, R.string.note_format_heading) {
            apply { text, sel -> MarkdownEditing.toggleLinePrefix(text, sel, "# ") }
        }
        FormattingButton(R.drawable.ic_format_list_bulleted, R.string.note_format_list) {
            apply { text, sel -> MarkdownEditing.toggleLinePrefix(text, sel, "- ") }
        }
        FormattingButton(R.drawable.ic_link, R.string.note_format_link) {
            apply { text, sel -> MarkdownEditing.insertLink(text, sel, linkPlaceholder) }
        }
    }
}

@Composable
private fun FormattingButton(
    @DrawableRes iconRes: Int,
    @StringRes descriptionRes: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            tint = AppTheme.colors.fieldOnDarkContent,
            modifier = Modifier.size(20.dp)
        )
    }
}