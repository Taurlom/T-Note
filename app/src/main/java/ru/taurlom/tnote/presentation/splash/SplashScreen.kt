package ru.taurlom.tnote.presentation.splash

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
import ru.taurlom.tnote.R
import ru.taurlom.tnote.presentation.theme.AppTheme

/**
 * Заставка-оверлей рисует тот же логотип, что и системный сплэш-экран,
 * поверх прогревающегося приложения (MainActivity): первый кадр обязан
 * совпадать с системным — тот же размер и центр экрана. Держится, пока
 * разделы не готовы, и мягко гаснет (alpha) — содержимое под ней уже
 * на месте, поэтому пустого экрана между заставкой и списками нет.
 *
 * Система (Android 12+) показывает adaptive-icon `splash_logo` в боксе
 * ~288dp, арт в нём занимает 66% холста ≈ 190dp. Без явного размера PNG
 * рисовался бы в «родных» пикселях и скачок на стыке был бы виден.
 * Если на конкретном устройстве кольцо всё же расходится — подстроить
 * эту константу (±несколько dp).
 */
private val SplashLogoSize = 190.dp

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "")

    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 10000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.launchBackground),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_center),
            contentDescription = null,
            modifier = Modifier.size(SplashLogoSize),
        )

        Image(
            painter = painterResource(R.drawable.logo_dial),
            contentDescription = null,
            modifier = Modifier
                .size(SplashLogoSize)
                .rotate(angle),
        )
    }
}
