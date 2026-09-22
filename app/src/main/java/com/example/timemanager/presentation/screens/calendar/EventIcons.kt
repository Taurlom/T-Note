package com.example.timemanager.presentation.screens.calendar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.timemanager.R
import com.example.timemanager.domain.model.EventIcon
import com.example.timemanager.domain.model.ScheduledEvent
import com.example.timemanager.domain.model.ScheduledEventType
import com.example.timemanager.presentation.theme.AppTheme

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
        if (isPast) AppTheme.colors.calendarPastMarker else defaultColor
    } else {
        if (isPast) Color(colorArgb).copy(alpha = 0.45f) else Color(colorArgb)
    }

/** Иконка типа события для выпадающего списка «Тип события». */
@get:DrawableRes
val ScheduledEventType.iconRes: Int
    get() = when (this) {
        ScheduledEventType.REGULAR -> R.drawable.ic_event_note
        ScheduledEventType.BIRTHDAY -> R.drawable.ic_redeem
        ScheduledEventType.REPEATING -> R.drawable.ic_event_chronic
        ScheduledEventType.WEEKEND -> R.drawable.ic_weekend
    }

/** Название типа события. */
@get:StringRes
val ScheduledEventType.labelRes: Int
    get() = when (this) {
        ScheduledEventType.REGULAR -> R.string.event_type_regular
        ScheduledEventType.BIRTHDAY -> R.string.event_type_birthday
        ScheduledEventType.REPEATING -> R.string.event_type_repeating
        ScheduledEventType.WEEKEND -> R.string.event_type_weekend
    }

/** Drawable для иконки обычного события (отображается под числом в календаре). */
@get:DrawableRes
val EventIcon.drawableRes: Int
    get() = when (this) {
        EventIcon.NOTE -> R.drawable.ic_event_note
        EventIcon.TRAVEL -> R.drawable.ic_event_travel
        EventIcon.FOREST -> R.drawable.ic_event_forest
        EventIcon.GIFTS -> R.drawable.ic_event_gifts
        EventIcon.HOME -> R.drawable.ic_event_home
        EventIcon.HEALTH -> R.drawable.ic_event_health
        EventIcon.BEACH -> R.drawable.ic_event_beach
        EventIcon.FOOTPRINT -> R.drawable.ic_event_footprint
        EventIcon.CELEBRATION -> R.drawable.ic_event_celebration
        EventIcon.BUILD -> R.drawable.ic_event_build
        EventIcon.FITNESS -> R.drawable.ic_event_fitness
        EventIcon.GROUPS -> R.drawable.ic_event_groups
        EventIcon.DELIVERY -> R.drawable.ic_event_delivery
        EventIcon.SELF_CARE -> R.drawable.ic_event_selfcare
        EventIcon.BAR -> R.drawable.ic_event_bar
        EventIcon.GARDEN -> R.drawable.ic_event_garden
    }

/** Подпись иконки события. */
@get:StringRes
val EventIcon.labelRes: Int
    get() = when (this) {
        EventIcon.NOTE -> R.string.icon_note
        EventIcon.TRAVEL -> R.string.icon_travel
        EventIcon.FOREST -> R.string.icon_forest
        EventIcon.GIFTS -> R.string.icon_gifts
        EventIcon.HOME -> R.string.icon_home
        EventIcon.HEALTH -> R.string.icon_health
        EventIcon.BEACH -> R.string.icon_beach
        EventIcon.FOOTPRINT -> R.string.icon_footprint
        EventIcon.CELEBRATION -> R.string.icon_celebration
        EventIcon.BUILD -> R.string.icon_build
        EventIcon.FITNESS -> R.string.icon_fitness
        EventIcon.GROUPS -> R.string.icon_groups
        EventIcon.DELIVERY -> R.string.icon_delivery
        EventIcon.SELF_CARE -> R.string.icon_selfcare
        EventIcon.BAR -> R.string.icon_bar
        EventIcon.GARDEN -> R.string.icon_garden
    }
