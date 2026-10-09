package ru.taurlom.tnote.presentation.screens.notes

import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.HeadingStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.channels.Channel
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.components.AppDialog
import ru.taurlom.tnote.presentation.components.AppSaveButton
import ru.taurlom.tnote.presentation.components.AppTextField
import ru.taurlom.tnote.presentation.theme.AppTheme
import kotlin.math.roundToInt

/**
 * Замена системного меню выделения («Вырезать/Копировать/…») своей панелью.
 *
 * Compose 1.12 показывает меню выделения через [TextContextMenuProvider] из
 * `LocalTextContextMenuToolbarProvider`; экран подменяет провайдер этим
 * классом, поэтому платформенный ActionMode не стартует вообще, а панель
 * рисуем мы сами ([NoteSelectionToolbar]) — как в Telegram, без наложений.
 *
 * Паттерн — как у внутреннего `BasicTextContextMenuProvider` из foundation:
 * suspend-вызов держит сессию открытой до [Session.close] либо отмены
 * корутины (снятие выделения/уход из редактора отменяет её само).
 */
internal class NoteTextToolbarProvider : TextContextMenuProvider {
    private val mutatorMutex = MutatorMutex()

    /** Открытая сессия панели; null — панель скрыта. */
    var session by mutableStateOf<Session?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val localSession = Session(dataProvider)
        mutatorMutex.mutate {
            try {
                session = localSession
                localSession.awaitClose()
            } finally {
                session = null
            }
        }
    }

    fun close() {
        session?.close()
    }

    class Session(val dataProvider: TextContextMenuDataProvider) : TextContextMenuSession {
        private val channel = Channel<Unit>()
        override fun close() {
            channel.trySend(Unit)
        }

        suspend fun awaitClose() {
            channel.receive()
        }
    }
}

/**
 * Панель над выделением с кнопками форматирования. Системные пункты
 * («Вырезать/Копировать/…») сознательно не показываем: панель заменяет
 * системное меню полностью, а не дополняет его.
 *
 * Позиция — границы выделения относительно контейнера-якоря [anchor]; если
 * над выделением места нет, панель уходит под него.
 *
 * Должна лежать внутри Box'а-якоря ПОСЛЕ редактора, чтобы рисоваться поверх.
 * Оверлей в том же окне: тап по кнопке не уходит в текстовое поле, выделение
 * и фокус не схлопываются; снятие выделения закрывает панель само.
 */
@Composable
internal fun BoxScope.NoteSelectionToolbar(
    provider: NoteTextToolbarProvider,
    state: RichTextState,
    anchor: () -> LayoutCoordinates?,
    onLinkClick: () -> Unit,
) {
    val session = provider.session ?: return
    val coords = anchor() ?: return
    if (!coords.isAttached) return

    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 6.dp.toPx() }

    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .onSizeChanged { toolbarSize = it }
            // contentBounds — snapshot-aware: панель следует за перетаскиванием
            // маркеров выделения без пересоздания сессии.
            .offset {
                val bounds = session.dataProvider.contentBounds(coords)
                val yAbove = bounds.top - toolbarSize.height - gapPx
                val y = if (yAbove >= 0f) yAbove else bounds.bottom + gapPx
                val maxX = (coords.size.width - toolbarSize.width).coerceAtLeast(0)
                IntOffset(bounds.left.roundToInt().coerceIn(0, maxX), y.roundToInt())
            },
        shape = MaterialTheme.shapes.medium,
        color = AppTheme.colors.dialogContainer,
        shadowElevation = 8.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            // Форматирование панель не закрывает: выделение живо, можно
            // применить несколько стилей подряд; активный стиль подсвечен.
            FormatButton(
                icon = R.drawable.ic_format_bold,
                contentDescription = stringResource(R.string.note_format_bold),
                active = state.currentSpanStyle.fontWeight == FontWeight.Bold,
            ) {
                state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
            }
            FormatButton(
                icon = R.drawable.ic_title,
                contentDescription = stringResource(R.string.note_format_heading),
                active = state.currentHeadingStyle == HeadingStyle.H1,
            ) {
                state.setHeadingStyle(
                    if (state.currentHeadingStyle == HeadingStyle.Normal) HeadingStyle.H1 else HeadingStyle.Normal,
                )
            }
            FormatButton(
                icon = R.drawable.ic_format_list_bulleted,
                contentDescription = stringResource(R.string.note_format_list),
                active = state.isUnorderedList,
            ) {
                state.toggleUnorderedList()
            }
            FormatButton(
                icon = R.drawable.ic_link,
                contentDescription = stringResource(R.string.note_format_link),
                active = state.isLink,
            ) {
                // Диалог заберёт фокус — панель закрываем сами; выделение
                // держит вызывающий (linkSelection на экране).
                session.close()
                onLinkClick()
            }
        }
    }
}

@Composable
private fun FormatButton(icon: Int, contentDescription: String, active: Boolean, onClick: () -> Unit) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = if (active) MaterialTheme.colorScheme.primary else AppTheme.colors.dialogContent,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(5.dp)
            .size(24.dp),
    )
}

/**
 * Диалог ввода адреса ссылки для выделенного текста. Диалог забирает фокус,
 * поэтому вызывающий запоминает выделение при открытии и восстанавливает его
 * перед `addLinkToSelection`, если оно схлопнулось.
 */
@Composable
internal fun NoteLinkDialog(initialUrl: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    AppDialog(
        title = stringResource(R.string.note_link_dialog_title),
        onDismissRequest = onDismiss,
        text = {
            AppTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.note_link_url_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            AppSaveButton(
                onClick = { onConfirm(url.trim()) },
                enabled = url.isNotBlank(),
            )
        },
    )
}
