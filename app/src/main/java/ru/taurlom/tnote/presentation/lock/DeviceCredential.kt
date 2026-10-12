package ru.taurlom.tnote.presentation.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Подтверждение «это владелец телефона» способом разблокировки устройства:
 * биометрия, а если её нет — PIN/графический ключ/пароль. Приложение не
 * видит и не хранит ни код, ни биометрию — системный диалог отвечает
 * только «подтверждён / нет».
 *
 * API 29+ — BiometricPrompt с откатом на код блокировки; на 24–28
 * BiometricPrompt код не умеет, там — Keyguard-диалог через intent.
 */
object DeviceCredential {

    /** Настроена ли на телефоне блокировка экрана (без неё защищать нечем). */
    fun isAvailable(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    /** API 29+: системный диалог биометрии с кнопкой «экранный код». */
    @RequiresApi(29)
    fun showPrompt(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                // Отмена (своя кнопка, системный жест, смена ориентации) —
                // тоже «не подтверждён»: вызывающая сторона остаётся
                // на заглушке с кнопкой повтора.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    /** API 24–28: интент системного подтверждения; null — блокировки нет. */
    fun buildKeyguardIntent(context: Context, title: String): Intent? = if (Build.VERSION.SDK_INT >= 29) {
        null
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(title, null)
    }

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
}

/**
 * Готовит функцию «показать системное подтверждение», скрывая различие
 * API: на 29+ вызывается BiometricPrompt, на 24–28 результат приходит
 * через activity-result. [onResult] вызывается ровно один раз за вызов.
 */
@Composable
fun rememberDeviceCredentialVerifier(title: String, onResult: (Boolean) -> Unit): () -> Unit {
    val activity = LocalContext.current as FragmentActivity
    val currentOnResult by rememberUpdatedState(onResult)
    val currentTitle by rememberUpdatedState(title)
    val keyguardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        currentOnResult(result.resultCode == Activity.RESULT_OK)
    }
    return {
        if (Build.VERSION.SDK_INT >= 29) {
            DeviceCredential.showPrompt(activity, currentTitle, currentOnResult)
        } else {
            val intent = DeviceCredential.buildKeyguardIntent(activity, currentTitle)
            if (intent == null) currentOnResult(false) else keyguardLauncher.launch(intent)
        }
    }
}
