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

/**
 * Runs the platform biometric check used to guard the remembered credentials. Android shows
 * `BiometricPrompt`, iOS evaluates the `LocalAuthentication` biometry policy; desktop has no
 * implementation and always reports [BiometricResult.Unavailable].
 */
interface BiometricAuthenticator {

    /** True when the device has biometrics enrolled and ready to use. */
    suspend fun isAvailable(): Boolean

    suspend fun authenticate(promptText: BiometricPromptText): BiometricResult
}

/**
 * Copy shown by the system prompt. Resolved in the UI layer so the domain does not depend on
 * Compose resources.
 */
data class BiometricPromptText(val title: String, val subtitle: String, val cancelLabel: String)

sealed interface BiometricResult {

    /** The user passed the biometric check. */
    data object Success : BiometricResult

    /** The user dismissed the prompt — the app stays locked. */
    data object Cancelled : BiometricResult

    /** Biometrics are missing, not enrolled or disabled on this device. */
    data object Unavailable : BiometricResult

    data class Failed(val message: String?) : BiometricResult
}

/** Returns the platform-default [BiometricAuthenticator]. */
expect fun defaultBiometricAuthenticator(): BiometricAuthenticator
