package ru.taurlom.tnote.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.taurlom.tnote.MainActivity
import ru.taurlom.tnote.R
import ru.taurlom.tnote.domain.model.ScheduledEvent
import ru.taurlom.tnote.domain.model.ScheduledEventType
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.domain.usecase.GetEventsForDateUseCase
import ru.taurlom.tnote.domain.util.formatEventClock
import ru.taurlom.tnote.presentation.screens.calendar.drawableRes
import ru.taurlom.tnote.presentation.screens.calendar.iconRes
import ru.taurlom.tnote.presentation.theme.AppThemeSpec
import ru.taurlom.tnote.presentation.theme.DarkTheme
import ru.taurlom.tnote.presentation.theme.LightTheme
import ru.taurlom.tnote.presentation.theme.themeSpecFor
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Виджет «Сегодня»: события текущего дня и нижняя панель из трёх действий —
 * «Списки», «Добавить заметку» и «Календарь». Панель повторяет нижнюю
 * навигацию приложения: тот же контейнер, иконки и акцентная «пилюля»
 * у главного действия.
 *
 * Данные берутся из того же [GetEventsForDateUseCase], что и уведомление-
 * сводка: повторы, дни рождения и многодневные события уже развёрнуты,
 * «Выходные» отфильтрованы. Glance не умеет ни Compose-экраны приложения,
 * ни TNoteTheme целиком, поэтому цвета виджета собираются из того же
 * [AppThemeSpec], что и у экранов: выбранная тема перекрашивает виджет.
 *
 * Обновления:
 *  - Room-наблюдатель [TodayWidgetUpdater] — сразу после правок календаря,
 *    CalDAV-синхронизации и смены темы;
 *  - `updatePeriodMillis` в `today_widget_info.xml` — раз в 30 минут,
 *    чтобы после полуночи сменилась дата и список событий.
 */
class TodayWidget : GlanceAppWidget() {

    // Exact, а не Responsive: высота 3×3 у разных лаунчеров заметно
    // отличается, и список событий должен занимать столько строк,
    // сколько реально помещается, а не фиксированный бакет.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dependencies = EntryPointAccessors.fromApplication(
            context,
            TodayWidgetEntryPoint::class.java,
        )
        val clock = dependencies.clock()
        val today = LocalDate.now(clock)
        val (theme, events) = withContext(Dispatchers.IO) {
            // Виджет не должен падать из-за ошибки чтения базы или настроек:
            // пустой список и системная тема честнее, чем «не удалось
            // загрузить» на весь экран.
            val theme = runCatching { dependencies.settingsRepository().selectedTheme.first() }
                .getOrDefault(ThemeKind.SYSTEM)
            val events = runCatching { dependencies.eventsForDate()(today, today) }
                .getOrDefault(emptyList())
            theme to events
        }
        val state = buildState(context, today, events)
        val colors = todayWidgetColors(theme)
        provideContent {
            GlanceTheme(colors = colors) {
                TodayWidgetContent(state)
            }
        }
    }
}

/** Доступ к графу Hilt из Glance-воркера (см. DailyDigestDependencies). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodayWidgetEntryPoint {
    fun eventsForDate(): GetEventsForDateUseCase
    fun settingsRepository(): SettingsRepository
    fun clock(): Clock
}

/**
 * Цвета виджета из выбранной темы приложения. Glance знает только роли
 * Material 3, поэтому раскладываем по ним роли нижней навигации:
 * surfaceVariant — контейнер панели, secondary — неактивные иконки,
 * secondaryContainer/onSecondaryContainer — акцентная «пилюля» и её иконка,
 * primaryContainer — логотип в шапке. Акцент для остальных элементов — из FAB.
 *
 * Для «как в системе» Glance сам выбирает день или ночь по конфигурации.
 */
private fun todayWidgetColors(theme: ThemeKind): ColorProviders = when (theme) {
    ThemeKind.SYSTEM -> ColorProviders(
        light = LightTheme.toWidgetScheme(),
        dark = DarkTheme.toWidgetScheme(),
    )

    else -> ColorProviders(themeSpecFor(theme, systemDark = false).toWidgetScheme())
}

private fun AppThemeSpec.toWidgetScheme(): ColorScheme {
    val scheme = colorScheme
    return scheme.copy(
        primary = colors.fabContainer,
        onPrimary = colors.fabContent,
        secondary = colors.bottomNavInactiveIcon,
        onSecondary = colors.bottomNavContainer,
        secondaryContainer = colors.bottomNavIndicator,
        onSecondaryContainer = colors.bottomNavActiveIcon,
        tertiary = colors.calendarEventMarker,
        primaryContainer = colors.brandLogo,
        surfaceVariant = colors.bottomNavContainer,
        outline = colors.outlinedButtonContent,
    )
}

/** Всё, что нужно композиции виджета: строки уже локализованы. */
private data class TodayWidgetState(
    val title: String,
    val date: String,
    val shortDate: String,
    val count: String?,
    val events: List<TodayWidgetEvent>,
)

/** Одна строка повестки: время уже в формате «10:00» (или null — весь день). */
private data class TodayWidgetEvent(val title: String, val time: String?, @get:DrawableRes val iconRes: Int, val colorArgb: Long)

private fun buildState(context: Context, today: LocalDate, events: List<ScheduledEvent>): TodayWidgetState {
    val locale = context.resources.configuration.locales[0]
    // Календарь сортирует события вручную (position), а повестке нужен
    // хронологический порядок: сначала события с часами, затем дневные
    // метки без времени.
    val ordered = events.sortedWith(
        compareBy<ScheduledEvent> { it.windowOn(today)?.first == null }
            .thenBy { it.windowOn(today)?.first ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase(locale) },
    )
    return TodayWidgetState(
        title = context.getString(R.string.widget_today_title),
        date = today.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale)),
        shortDate = today.format(DateTimeFormatter.ofPattern("d MMM", locale)),
        count = if (ordered.isEmpty()) {
            null
        } else {
            context.resources.getQuantityString(R.plurals.widget_event_count, ordered.size, ordered.size)
        },
        events = ordered.map { event -> event.toWidgetEvent(context, today) },
    )
}

private fun ScheduledEvent.toWidgetEvent(context: Context, today: LocalDate): TodayWidgetEvent {
    val window = windowOn(today)
    // Событие длиной в целые сутки (или средний день многодневного)
    // показываем без времени: «00:00 – 24:00» только шумит.
    val allDay = window != null &&
        window.first == 0 &&
        window.second == ScheduledEvent.MINUTES_PER_DAY
    val start = if (allDay) null else window?.first
    val end = if (allDay) null else window?.second
    return TodayWidgetEvent(
        title = title,
        time = when {
            start != null -> formatEventClock(start)
            end != null -> context.getString(R.string.event_time_until, formatEventClock(end))
            else -> null
        },
        iconRes = when (type) {
            ScheduledEventType.REGULAR -> icon.drawableRes

            // День рождения, повторение и «Выходной» рисуются типом,
            // а не выбранной иконкой — как в календаре.
            else -> type.iconRes
        },
        colorArgb = colorArgb,
    )
}

@Composable
private fun TodayWidgetContent(state: TodayWidgetState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val compactHeader = size.height < 140.dp
    val capacity = visibleEventCapacity(size, compactHeader)
    val hasMore = state.events.size > capacity
    val visibleEvents = state.events.take(
        if (hasMore) (capacity - 1).coerceAtLeast(1) else state.events.size,
    )
    val hiddenCount = state.events.size - visibleEvents.size

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .appWidgetBackground()
            // Слева и справа по 8dp: текст и события стоят ближе к краю,
            // как в макете. Сверху 8dp — логотип чуть выше шапки.
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
    ) {
        Header(state = state, compact = compactHeader)
        Spacer(GlanceModifier.height(2.dp))
        if (visibleEvents.isEmpty()) {
            EmptyState(modifier = GlanceModifier.defaultWeight())
        } else {
            Column(modifier = GlanceModifier.defaultWeight()) {
                visibleEvents.forEach { event -> EventRow(event) }
                if (hasMore && capacity >= 2 && hiddenCount > 0) {
                    Text(
                        text = context.getString(R.string.widget_more_events, hiddenCount),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        TodayWidgetNavBar()
    }
}

/**
 * Сколько строк событий помещается в виджет. Высота строки — 20dp
 * (16dp иконка + 4dp отступы), остальное — шапка, нижняя панель и поля.
 */
private fun visibleEventCapacity(size: DpSize, compactHeader: Boolean): Int {
    val chrome = 20f +
        (if (compactHeader) 28f else 36f) +
        2f +
        4f +
        48f
    return ((size.height.value - chrome) / 20f).toInt().coerceIn(1, 7)
}

/**
 * Шапка: иконка приложения слева, справа — «Сегодня» с количеством событий
 * на одной строке и дата под ними. Ширина иконки и отступа подобрана так,
 * чтобы текст начинался на той же вертикали, что иконки событий (52dp
 * от края виджета): это держит шапку и список в одной колонке.
 */
@Composable
private fun Header(state: TodayWidgetState, compact: Boolean) {
    val context = LocalContext.current
    val iconSize = if (compact) 28.dp else 36.dp
    // 8dp отступа снаружи + 44dp = 52dp — общая вертикаль шапки и событий.
    val iconGap = if (compact) 16.dp else 8.dp
    val titleSize = if (compact) 14.sp else 16.sp
    val dateSize = if (compact) 10.sp else 11.sp
    val countSize = 9.sp

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.logo_vector),
            contentDescription = context.getString(R.string.widget_open_app),
            modifier = GlanceModifier
                .size(iconSize)
                .clickable(openAppAction(context)),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primaryContainer),
        )
        Spacer(GlanceModifier.width(iconGap))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                state.count?.let { count ->
                    // Небольшой зазор: «Сегодня» и «4 события» должны
                    // помещаться на одной строке даже в узком 3×3.
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = count,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = countSize,
                        ),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = if (compact) state.shortDate else state.date,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = dateSize,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.widget_no_events),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun EventRow(event: TodayWidgetEvent) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.time != null) {
            Text(
                text = event.time,
                modifier = GlanceModifier.width(44.dp),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        } else {
            Spacer(GlanceModifier.width(44.dp))
        }
        Image(
            provider = ImageProvider(event.iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(
                if (event.colorArgb == ScheduledEvent.DEFAULT_COLOR) {
                    GlanceTheme.colors.tertiary
                } else {
                    // Цвет выбран пользователем — он не зависит от темы.
                    ColorProvider(Color(event.colorArgb))
                },
            ),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = event.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}

/**
 * Нижняя панель виджета в стиле нижней навигации приложения: общий
 * контейнер, три
 * равных действия и акцентная «пилюля» у добавления заметки. Подписей нет —
 * в приложении нижняя навигация тоже иконная, а действия описаны для
 * accessibility через contentDescription.
 */
@Composable
private fun TodayWidgetNavBar() {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(16.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavAction(
            iconRes = R.drawable.ic_list_alt,
            contentDescription = context.getString(R.string.widget_action_lists),
            action = openListsAction(context),
            modifier = GlanceModifier.defaultWeight(),
        )
        NavAction(
            iconRes = R.drawable.ic_note_stack_add,
            contentDescription = context.getString(R.string.widget_action_add_note),
            action = newNoteAction(context),
            modifier = GlanceModifier.defaultWeight(),
            active = true,
        )
        NavAction(
            iconRes = R.drawable.ic_calendar_month,
            contentDescription = context.getString(R.string.widget_action_calendar),
            action = openCalendarAction(context),
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun NavAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
    active: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(iconRes),
                    contentDescription = contentDescription,
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                )
            }
        } else {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = contentDescription,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondary),
            )
        }
    }
}

private fun openAppAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

private fun newNoteAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_NEW_NOTE, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

private fun openListsAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_LISTS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)

private fun openCalendarAction(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_CALENDAR, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
)
