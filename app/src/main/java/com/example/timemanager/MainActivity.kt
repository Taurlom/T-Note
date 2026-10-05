package com.example.timemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.compose.ui.Modifier
import com.example.timemanager.domain.model.AppFont
import com.example.timemanager.domain.model.SharedText
import com.example.timemanager.domain.model.ThemeKind
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.presentation.navigation.AppNavigation
import com.example.timemanager.presentation.splash.SplashScreen
import com.example.timemanager.presentation.theme.TNoteTheme
import com.example.timemanager.presentation.theme.fontFamily
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Compose-состояние вне composition: пишет его onNewIntent, читает AppNavigation.
    private val pendingShareUri = mutableStateOf<Uri?>(null)

    // Текст из «Поделиться» — тем же маршрутом, что и Uri списка: не файл
    // в SAF, а пара строк, целиком живущая в интенте.
    private val pendingSharedText = mutableStateOf<SharedText?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splashView ->
            splashView.remove()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShare(intent)
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
                theme = selectedTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        AppNavigation(
                            pendingShareUri = pendingShareUri.value,
                            onPendingShareUriHandled = { pendingShareUri.value = null },
                            pendingSharedText = pendingSharedText.value,
                            onPendingSharedTextHandled = { pendingSharedText.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode="singleTop": файл, открытый в запущенном приложении,
        // приходит сюда, а не в новую активность.
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
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
                            text = text.toString()
                        )
                    }
                } else {
                    // «Поделиться → T-Note» файлом .tnote: он лежит в EXTRA_STREAM.
                    @Suppress("DEPRECATION")
                    (intent.extras?.getParcelable(Intent.EXTRA_STREAM) as? Uri)
                        ?.let { pendingShareUri.value = it }
                }
            }
        }
    }
}
