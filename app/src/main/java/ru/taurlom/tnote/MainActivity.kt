package ru.taurlom.tnote

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.SharedText
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.presentation.navigation.AppNavigation
import ru.taurlom.tnote.presentation.screens.settings.SettingsViewModel
import ru.taurlom.tnote.presentation.splash.SplashScreen
import ru.taurlom.tnote.presentation.theme.TNoteTheme
import ru.taurlom.tnote.presentation.theme.fontFamily
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Тот же экземпляр, что получит AppNavigation (hiltViewModel с owner =
    // activity): заставка ждёт его sectionsLoaded — когда разделы прочитаны
    // из DataStore, под заставкой уже нарисован интерфейс.
    private val settingsViewModel: SettingsViewModel by viewModels()

    // Compose-состояние вне composition: пишет его onNewIntent, читает AppNavigation.
    private val pendingShareUri = mutableStateOf<Uri?>(null)

    // Текст из «Поделиться» — тем же маршрутом, что и Uri списка: не файл
    // в SAF, а пара строк, целиком живущая в интенте.
    private val pendingSharedText = mutableStateOf<SharedText?>(null)

    // Тап по уведомлению-сводке: открыть раздел «Календарь». Тем же
    // маршрутом, что и шэры, — состояние вне composition.
    private val pendingOpenCalendar = mutableStateOf(false)

    companion object {
        /**
         * Extra из уведомления-сводки (ставит DailyDigestNotifier в слое
         * данных): тап должен открыть календарь, а не раздел, который
         * случился открытым.
         */
        const val EXTRA_OPEN_CALENDAR = "open_calendar"

        /** Минимальное время заставки — «мигнуть брендом» (мс). */
        private const val SPLASH_MIN_MILLIS = 500L

        /** Фейд системного сплэша поверх нашего оверлея (мс). */
        private const val SPLASH_SYSTEM_FADE_MILLIS = 200L

        /** Длительность фейда заставки (мс). */
        private const val SPLASH_FADE_MILLIS = 250

        /** Запас на кадр после фейда, прежде чем снять оверлей (мс). */
        private const val SPLASH_FADE_TAIL_MILLIS = 60
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Системный сплэш гаснет не мгновенно, а фейдом поверх нашего
        // оверлея: два разных рисунка логотипа (системная adaptive-icon
        // и нативная отрисовка SplashScreen) перекрываются плавно,
        // вместо рывка на стыке. Снятие обязано быть после анимации —
        // зарегистрированный листенер забирает ответственность за удаление.
        installSplashScreen().setOnExitAnimationListener { splashView ->
            splashView.view
                .animate()
                .alpha(0f)
                .setDuration(SPLASH_SYSTEM_FADE_MILLIS)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashView.remove()
                    }
                })
                .start()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            val selectedFont by settingsRepository.selectedFont
                .collectAsState(initial = AppFont.PT_SANS)
            val selectedTheme by settingsRepository.selectedTheme
                .collectAsState(initial = ThemeKind.DARK)
            val settingsState by settingsViewModel.uiState.collectAsState()

            // Заставка — оверлей поверх уже композирующегося приложения:
            // ViewModels, Room и DataStore прогреваются ПОД ней (а не после,
            // как раньше), поэтому к моменту, когда заставка гаснет, разделы
            // уже на экране — без пустого промежутка. Держим её, пока
            // разделы не прочитаны (sectionsLoaded), но не меньше
            // SPLASH_MIN_MILLIS — «мигнуть брендом, а не задерживать вход».
            // Состояние saveable: поворот экрана не перезапускает таймеры.
            var splashMinTimePassed by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(SPLASH_MIN_MILLIS)
                splashMinTimePassed = true
            }
            val splashDone = splashMinTimePassed && settingsState.sectionsLoaded
            var splashVisible by rememberSaveable { mutableStateOf(true) }
            val splashAlpha by animateFloatAsState(
                targetValue = if (splashDone) 0f else 1f,
                animationSpec = tween(SPLASH_FADE_MILLIS),
                label = "splashAlpha",
            )
            LaunchedEffect(splashDone) {
                if (splashDone && splashVisible) {
                    // Оверлей снимаем после доигрывания фейда (запас на кадр):
                    // до этого он лежит с alpha 0 и не мешает экрану.
                    delay((SPLASH_FADE_MILLIS + SPLASH_FADE_TAIL_MILLIS).toLong())
                    splashVisible = false
                }
            }

            TNoteTheme(
                fontFamily = selectedFont.fontFamily,
                theme = selectedTheme,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            pendingShareUri = pendingShareUri.value,
                            onPendingShareUriHandled = { pendingShareUri.value = null },
                            pendingSharedText = pendingSharedText.value,
                            onPendingSharedTextHandled = { pendingSharedText.value = null },
                            pendingOpenCalendar = pendingOpenCalendar.value,
                            onPendingOpenCalendarHandled = { pendingOpenCalendar.value = false },
                        )
                        if (splashVisible) {
                            SplashScreen(modifier = Modifier.alpha(splashAlpha))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTop": файл, открытый в запущенном приложении,
        // приходит сюда, а не в новую активность.
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        // Уведомление-сводку тапают без action — только наш extra.
        if (intent?.getBooleanExtra(EXTRA_OPEN_CALENDAR, false) == true) {
            pendingOpenCalendar.value = true
        }
        when (intent?.action) {
            Intent.ACTION_VIEW ->
                intent.data?.let { pendingShareUri.value = it }
            Intent.ACTION_SEND -> {
                // Текст из «Поделиться» (text/plain): браузер кладёт название
                // страницы в EXTRA_SUBJECT, выделение или ссылку — в EXTRA_TEXT.
                // Пустой текст не принимаем: диалог «сохранить пустоту»
                // только сбивает с толку. Если текста нет, а type text/plain
                // (приложение шэрит текстовый файл), уходим в файловую ветку —
                // там честная ошибка разбора, а не молчаливое «ничего не
                // произошло».
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                if (intent.type.equals("text/plain", ignoreCase = true) && text != null) {
                    if (text.isNotBlank()) {
                        pendingSharedText.value = SharedText(
                            subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                            text = text.toString(),
                        )
                    }
                } else {
                    // «Поделиться > T-Note» файлом .tnote: он лежит в EXTRA_STREAM.
                    @Suppress("DEPRECATION")
                    (intent.extras?.getParcelable(Intent.EXTRA_STREAM) as? Uri)
                        ?.let { pendingShareUri.value = it }
                }
            }
        }
    }
}
