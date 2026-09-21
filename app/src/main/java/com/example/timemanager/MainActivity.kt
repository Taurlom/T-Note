package com.example.timemanager

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.timemanager.domain.repository.SettingsRepository
import com.example.timemanager.presentation.navigation.AppNavigation
import com.example.timemanager.presentation.splash.SplashScreen
import com.example.timemanager.presentation.theme.AppFont
import com.example.timemanager.presentation.theme.TNoteTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Передача системного сплэша заставке: системная анимация «сжаться
        // и исчезнуть» дала бы видимый скачок. Первый кадр SplashScreen()
        // идентичен системному логотипу, поэтому убираем сплэш мгновенно.
        installSplashScreen().setOnExitAnimationListener { splashView ->
            splashView.remove()
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val selectedFont by settingsRepository.selectedFont
                .collectAsState(initial = AppFont.PT_SANS)

            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(2000)
                showSplash = false
            }

            TNoteTheme(fontFamily = selectedFont.fontFamily) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Кросс-фейд заставки в приложение: заставка гаснет
                    // плавно, а не исчезает одним кадром.
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!showSplash) {
                            AppNavigation()
                        }
                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(tween(400))
                        ) {
                            SplashScreen()
                        }
                    }
                }
            }
        }
    }
}
