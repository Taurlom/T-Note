package ru.taurlom.tnote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import ru.taurlom.tnote.domain.model.AppFont
import ru.taurlom.tnote.domain.model.SharedText
import ru.taurlom.tnote.domain.model.ThemeKind
import ru.taurlom.tnote.domain.repository.SettingsRepository
import ru.taurlom.tnote.presentation.navigation.AppNavigation
import ru.taurlom.tnote.presentation.splash.SplashScreen
import ru.taurlom.tnote.presentation.theme.TNoteTheme
import ru.taurlom.tnote.presentation.theme.fontFamily
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Compose-��������� ��� composition: ����� ��� onNewIntent, ������ AppNavigation.
    private val pendingShareUri = mutableStateOf<Uri?>(null)

    // ����� �� ������������ � ��� �� ���������, ��� � Uri ������: �� ����
    // � SAF, � ���� �����, ������� ������� � �������.
    private val pendingSharedText = mutableStateOf<SharedText?>(null)

    // ��� �� �����������-������: ������� ������ �����������. ��� ��
    // ���������, ��� � ����, � ��������� ��� composition.
    private val pendingOpenCalendar = mutableStateOf(false)

    companion object {
        /**
         * Extra �� �����������-������ (������ DailyDigestNotifier � ����
         * ������): ��� ������ ������� ���������, � �� ������, �������
         * �������� ��������.
         */
        const val EXTRA_OPEN_CALENDAR = "open_calendar"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splashView ->
            splashView.remove()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            val selectedFont by settingsRepository.selectedFont
                .collectAsState(initial = AppFont.PT_SANS)
            val selectedTheme by settingsRepository.selectedTheme
                .collectAsState(initial = ThemeKind.DARK)

            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(2000)
                showSplash = false
            }

            TNoteTheme(
                fontFamily = selectedFont.fontFamily,
                theme = selectedTheme,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        AppNavigation(
                            pendingShareUri = pendingShareUri.value,
                            onPendingShareUriHandled = { pendingShareUri.value = null },
                            pendingSharedText = pendingSharedText.value,
                            onPendingSharedTextHandled = { pendingSharedText.value = null },
                            pendingOpenCalendar = pendingOpenCalendar.value,
                            onPendingOpenCalendarHandled = { pendingOpenCalendar.value = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTop": ����, �������� � ���������� ����������,
        // �������� ����, � �� � ����� ����������.
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        // �����������-������ ������ ��� action � ������ ��� extra.
        if (intent?.getBooleanExtra(EXTRA_OPEN_CALENDAR, false) == true) {
            pendingOpenCalendar.value = true
        }
        when (intent?.action) {
            Intent.ACTION_VIEW ->
                intent.data?.let { pendingShareUri.value = it }
            Intent.ACTION_SEND -> {
                // ����� �� ������������ (text/plain): ������� ����� ��������
                // �������� � EXTRA_SUBJECT, ��������� ��� ������ � � EXTRA_TEXT.
                // ������ ����� �� ���������: ������ ���������� �������
                // ������ ������� � �����. ���� ������ ���, � type text/plain
                // (���������� ����� ��������� ����), ������ � �������� ����� �
                // ��� ������� ������ �������, � �� ���������� ������� ��
                // ���������.
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                if (intent.type.equals("text/plain", ignoreCase = true) && text != null) {
                    if (text.isNotBlank()) {
                        pendingSharedText.value = SharedText(
                            subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                            text = text.toString(),
                        )
                    }
                } else {
                    // ����������� > T-Note� ������ .tnote: �� ����� � EXTRA_STREAM.
                    @Suppress("DEPRECATION")
                    (intent.extras?.getParcelable(Intent.EXTRA_STREAM) as? Uri)
                        ?.let { pendingShareUri.value = it }
                }
            }
        }
    }
}
