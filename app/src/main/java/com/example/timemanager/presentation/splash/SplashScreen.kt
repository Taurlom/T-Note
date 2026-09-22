package com.example.timemanager.presentation.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.presentation.theme.AppTheme

/**
 * Заставка рисует тот же логотип, что и системный сплэш-экран, — поэтому
 * первый кадр обязан совпадать с системным: тот же размер и центр экрана.
 *
 * Система (Android 12+) показывает adaptive-icon `splash_logo` в боксе
 * ~288dp, арт в нём занимает 66% холста ≈ 190dp. Без явного размера PNG
 * рисовался бы в «родных» пикселях и скачок на стыке был бы виден.
 * Если на конкретном устройстве кольцо всё же расходится — подстроить
 * эту константу (±несколько dp).
 */
private val SplashLogoSize = 190.dp

@Composable
fun SplashScreen() {

    val transition = rememberInfiniteTransition(label = "")

    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = ""
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.launchBackground),
        contentAlignment = Alignment.Center
    ) {

        Image(
            painter = painterResource(R.drawable.logo_center),
            contentDescription = null,
            modifier = Modifier.size(SplashLogoSize)
        )

        Image(
            painter = painterResource(R.drawable.logo_dial),
            contentDescription = null,
            modifier = Modifier
                .size(SplashLogoSize)
                .rotate(angle)
        )
    }
}
