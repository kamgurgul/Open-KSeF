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

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/** Face ID / Touch ID gate backed by `LocalAuthentication`. */
@OptIn(ExperimentalForeignApi::class)
class IosBiometricAuthenticator : BiometricAuthenticator {

    override suspend fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    override suspend fun authenticate(promptText: BiometricPromptText): BiometricResult =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            context.localizedCancelTitle = promptText.cancelLabel
            // Hide the passcode fallback - the login form is the fallback in this app.
            context.localizedFallbackTitle = ""

            if (!context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)) {
                continuation.resume(BiometricResult.Unavailable)
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                policy = LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                localizedReason = promptText.subtitle,
            ) { success, error ->
                if (!continuation.isActive) return@evaluatePolicy
                val result =
                    when {
                        success -> BiometricResult.Success
                        error == null -> BiometricResult.Failed(null)
                        error.code in CANCELLED_CODES -> BiometricResult.Cancelled
                        error.code in UNAVAILABLE_CODES -> BiometricResult.Unavailable
                        else -> BiometricResult.Failed(error.localizedDescription)
                    }
                continuation.resume(result)
            }
        }

    private companion object {
        val CANCELLED_CODES =
            setOf(LAErrorUserCancel, LAErrorAppCancel, LAErrorSystemCancel, LAErrorUserFallback)
        val UNAVAILABLE_CODES = setOf(LAErrorBiometryNotAvailable, LAErrorBiometryNotEnrolled)
    }
}

actual fun defaultBiometricAuthenticator(): BiometricAuthenticator = IosBiometricAuthenticator()
