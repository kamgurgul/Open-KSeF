/*
 * Copyright KG Soft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kgurgul.openksef.domain.biometric

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

class AndroidBiometricAuthenticator(context: Context) : BiometricAuthenticator {

    private val appContext = context.applicationContext

    /**
     * Host of the prompt, bound to the composition by [RegisterBiometricHost]. Null while no
     * activity is attached, which makes the prompt report [BiometricResult.Unavailable].
     */
    var activity: ComponentActivity? = null

    override suspend fun isAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(promptText: BiometricPromptText): BiometricResult {
        val activity = activity ?: return BiometricResult.Unavailable
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val callback =
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            if (continuation.isActive) continuation.resume(BiometricResult.Success)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(toResult(errorCode, errString))
                            }
                        }

                        // Not terminal - a single unrecognised fingerprint keeps the prompt open.
                        override fun onAuthenticationFailed() = Unit
                    }
                // Activity-hosted overload: it keeps the prompt out of the fragment manager, so a
                // plain ComponentActivity from LocalActivity is enough to host it. The Runnable is
                // only used for the device-credential fallback, which this prompt does not allow.
                val prompt =
                    BiometricPrompt(
                        activity,
                        activity,
                        activity,
                        {},
                        ContextCompat.getMainExecutor(appContext),
                        callback,
                    )
                val promptInfo =
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(promptText.title)
                        .setSubtitle(promptText.subtitle)
                        .setNegativeButtonText(promptText.cancelLabel)
                        .setAllowedAuthenticators(AUTHENTICATORS)
                        .setConfirmationRequired(false)
                        .build()

                continuation.invokeOnCancellation {
                    activity.runOnUiThread { prompt.cancelAuthentication() }
                }
                prompt.authenticate(promptInfo)
            }
        }
    }

    private fun toResult(errorCode: Int, errString: CharSequence): BiometricResult =
        when (errorCode) {
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> BiometricResult.Unavailable
            else -> BiometricResult.Failed(errString.toString())
        }

    private companion object {
        // Class 3 biometrics only - the prompt guards the remembered KSeF token.
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}

actual fun defaultBiometricAuthenticator(): BiometricAuthenticator =
    AndroidBiometricAuthenticator(KoinPlatform.getKoin().get<Context>())
