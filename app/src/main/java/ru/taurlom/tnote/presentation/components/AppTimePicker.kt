package ru.taurlom.tnote.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.taurlom.tnote.presentation.theme.AppTheme
import ru.taurlom.tnote.presentation.theme.appTimePickerColors

/**
 * TimePicker в стиле приложения: тёмная подложка + цвета из ролей темы.
 *
 * Зачем подложка: пикер свой фон не рисует (containerColor в material3
 * 1.3.1 хранится, но не отрисовывается), поэтому его элементы висят прямо
 * на фоне диалога. Двоеточие между «ЧЧ»/«ММ» красится системным onSurface
 * и не параметризуется — на светлых диалогах (Dark/Forest/Autumn, где
 * dialogContainer светлый) оно выходило светлым на светлом. Тёмная
 * подложка решает это без перекрытия системных ролей: onSurface — светлый
 * текст — встаёт на тёмный фон, как ему и положено. Заодно подложка
 * повторяет анатомию экрана: pickerBackdrop — «фон», pickerDial — «карточки».
 */
@OptIn(ExperimentalMaterial3Api::class) // TimePicker и State — экспериментальный M3 API
@Composable
fun AppTimePicker(
    state: TimePickerState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        // Форма — единая с AppDialog (MaterialTheme.shapes.small).
        color = AppTheme.colors.pickerBackdrop,
        shape = MaterialTheme.shapes.small
    ) {
        TimePicker(
            state = state,
            // Зазор, чтобы чипы и циферблат не упирались в край подложки.
            modifier = Modifier.padding(8.dp),
            colors = appTimePickerColors()
        )
    }
}
