package com.example.timemanager.presentation.screens.calendar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.timemanager.R
import com.example.timemanager.domain.model.EventIcon
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.presentation.theme.PastEventMarker

/**
 * Палитра цветов иконки события. Значение [ScheduledEvent.DEFAULT_COLOR]
 * хранится отдельно и означает «красить тематическим цветом».
 */
val eventColorPresets: List<Long> = listOf(
    0xFFE53935, // красный
    0xFFF4511E, // терракотовый
    0xFFFFB300, // янтарный
    0xFF43A047, // зелёный
    0xFF00897B, // бирюзовый
    0xFF039BE5, // голубой
    0xFF1E88E5, // синий
    0xFF8E24AA, // фиолетовый
    0xFFD81B60, // розовый
    0xFF6D4C41  // коричневый
)

/**
 * Цвет иконки события: выбранный или [defaultColor] для «по умолчанию».
 * Прошедшие дни приглушаются — тёмным маркером или прозрачностью.
 */
@Composable
fun eventIconColor(colorArgb: Long, isPast: Boolean, defaultColor: Color): Color =
    if (colorArgb == ScheduledEvent.DEFAULT_COLOR) {
        if (isPast) PastEventMarker else defaultColor
    } else {
        if (isPast) Color(colorArgb).copy(alpha = 0.45f) else Color(colorArgb)
    }

/** Иконка типа события для выпадающего списка «Тип события». */
@get:DrawableRes
val ScheduledEventType.iconRes: Int
    get() = when (this) {
        ScheduledEventType.REGULAR -> R.drawable.ic_event_note
        ScheduledEventType.BIRTHDAY -> R.drawable.ic_redeem
    }

/** Название типа события. */
@get:StringRes
val ScheduledEventType.labelRes: Int
    get() = when (this) {
        ScheduledEventType.REGULAR -> R.string.event_type_regular
        ScheduledEventType.BIRTHDAY -> R.string.event_type_birthday
    }

/** Drawable для иконки обычного события (отображается под числом в календаре). */
@get:DrawableRes
val EventIcon.drawableRes: Int
    get() = when (this) {
        EventIcon.NOTE -> R.drawable.ic_event_note
        EventIcon.MEETING -> R.drawable.ic_event_meeting
        EventIcon.REPAIR -> R.drawable.ic_event_repair
        EventIcon.HOSPITAL -> R.drawable.ic_event_hospital
        EventIcon.TRAVEL -> R.drawable.ic_event_travel
    }

/** Подпись иконки события. */
@get:StringRes
val EventIcon.labelRes: Int
    get() = when (this) {
        EventIcon.NOTE -> R.string.icon_note
        EventIcon.MEETING -> R.string.icon_meeting
        EventIcon.REPAIR -> R.string.icon_repair
        EventIcon.HOSPITAL -> R.string.icon_hospital
        EventIcon.TRAVEL -> R.string.icon_travel
    }
