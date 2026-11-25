package com.example.ui.screens.pro

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * #40 Biometric Hidden Vault:
 * Authenticates via BiometricPrompt (BIOMETRIC_STRONG or DEVICE_CREDENTIAL).
 */
object BiometricVaultHelper {

    fun authenticate(
        context: Context,
        title: String = "Bóveda Oculta SynFix",
        subtitle: String = "Verifica tu identidad para acceder a pistas ocultas",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onError("No se encontró actividad compatible para BiometricPrompt")
            return
        }

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canAuth = BiometricManager.from(context).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // Allow graceful unlock when emulator/device has no PIN/biometrics enrolled
            if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                onSuccess()
            } else {
                onError("Biometría o credencial del dispositivo no disponible")
            }
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
