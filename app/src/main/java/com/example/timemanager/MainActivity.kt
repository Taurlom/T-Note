package com.example.timemanager

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        installSplashScreen()

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
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
