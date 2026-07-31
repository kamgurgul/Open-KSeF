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

package com.kgurgul.openksef.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.KsefLogger
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.domain.biometric.BiometricPromptText
import com.kgurgul.openksef.domain.biometric.BiometricResult
import com.kgurgul.openksef.domain.invoke
import com.kgurgul.openksef.domain.model.KsefEnvironment
import com.kgurgul.openksef.domain.result.AuthenticateBiometricInteractor
import com.kgurgul.openksef.domain.result.GetSavedCredentialsInteractor
import com.kgurgul.openksef.domain.result.InitSessionInteractor
import com.kgurgul.openksef.domain.result.IsBiometricAvailableInteractor
import com.kgurgul.openksef.domain.result.PersistCredentialsInteractor
import com.kgurgul.openksef.domain.result.SetEnvironmentInteractor
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_biometric_failed
import openksef.shared.generated.resources.error_biometric_unavailable
import openksef.shared.generated.resources.error_login_failed
import openksef.shared.generated.resources.error_nip_invalid
import openksef.shared.generated.resources.error_token_required

/**
 * [isLocked] is true when remembered credentials are guarded by biometrics - the form is replaced
 * by the unlock screen until the user passes the check.
 */
data class LoginUiState(
    val nip: String = "",
    val token: String = "",
    val environment: KsefEnvironment = KsefEnvironment.TEST,
    val rememberCredentials: Boolean = false,
    val biometricsAvailable: Boolean = false,
    val requireBiometrics: Boolean = false,
    val isLocked: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

/** One-shot events emitted by [LoginViewModel]. */
sealed interface LoginEvent {
    data object LoginSuccess : LoginEvent
}

class LoginViewModel(
    private val autoLogin: Boolean,
    private val initSessionInteractor: InitSessionInteractor,
    private val setEnvironmentInteractor: SetEnvironmentInteractor,
    private val getSavedCredentialsInteractor: GetSavedCredentialsInteractor,
    private val persistCredentialsInteractor: PersistCredentialsInteractor,
    private val isBiometricAvailableInteractor: IsBiometricAvailableInteractor,
    private val authenticateBiometricInteractor: AuthenticateBiometricInteractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = eventChannel.receiveAsFlow()

    private var biometricJob: Job? = null

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        viewModelScope.launch {
            val biometricsAvailable = isBiometricAvailableInteractor()
            val saved = getSavedCredentialsInteractor()
            val hasSavedCredentials = saved.nip != null && saved.token != null
            // A device that lost its biometric enrollment must not lock the user out.
            val requireBiometrics = saved.requireBiometrics && biometricsAvailable

            _uiState.update { state ->
                state.copy(
                    nip = saved.nip ?: "",
                    token = saved.token ?: "",
                    environment = saved.environment,
                    rememberCredentials = saved.nip != null,
                    biometricsAvailable = biometricsAvailable,
                    requireBiometrics = requireBiometrics,
                    isLocked = autoLogin && hasSavedCredentials && requireBiometrics,
                )
            }
            // Sign in automatically when the user opted to remember the credentials. Skipped for
            // logout / session-expiry redirects (autoLogin is true only on app start) and when
            // biometrics guard the credentials - then the unlock screen drives the login.
            if (autoLogin && hasSavedCredentials && !requireBiometrics) {
                login()
            }
        }
    }

    /** Runs the system biometric prompt and signs in with the remembered credentials on success. */
    fun onBiometricUnlockClick(promptText: BiometricPromptText) {
        if (!_uiState.value.isLocked) return
        biometricJob?.cancel()
        biometricJob = viewModelScope.launch {
            when (val result = authenticateBiometricInteractor(promptText)) {
                BiometricResult.Success -> {
                    _uiState.update { it.copy(isLocked = false, error = null) }
                    login()
                }
                // Dismissed by the user - stay locked so the prompt can be retried.
                BiometricResult.Cancelled -> Unit
                BiometricResult.Unavailable ->
                    _uiState.update {
                        it.copy(
                            isLocked = false,
                            biometricsAvailable = false,
                            requireBiometrics = false,
                            error = UiText.Resource(Res.string.error_biometric_unavailable),
                        )
                    }
                is BiometricResult.Failed ->
                    _uiState.update {
                        it.copy(
                            error =
                                result.message?.let { msg -> UiText.Raw(msg) }
                                    ?: UiText.Resource(Res.string.error_biometric_failed)
                        )
                    }
            }
        }
    }

    /** Leaves the unlock screen and shows the regular login form. */
    fun onUseCredentialsClick() {
        biometricJob?.cancel()
        _uiState.update { it.copy(isLocked = false, error = null) }
    }

    fun onNipChanged(nip: String) {
        _uiState.update { it.copy(nip = nip, error = null) }
    }

    fun onTokenChanged(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun onEnvironmentChanged(environment: KsefEnvironment) {
        _uiState.update { it.copy(environment = environment) }
        viewModelScope.launch { setEnvironmentInteractor(environment) }
    }

    fun onRememberChanged(remember: Boolean) {
        _uiState.update {
            it.copy(
                rememberCredentials = remember,
                requireBiometrics = if (remember) it.requireBiometrics else false,
            )
        }
    }

    fun onRequireBiometricsChanged(required: Boolean) {
        _uiState.update { it.copy(requireBiometrics = required) }
    }

    fun login() {
        val state = _uiState.value
        val nip = state.nip.trim()
        val token = state.token.trim()

        if (nip.length != 10 || !nip.all { it.isDigit() }) {
            _uiState.update { it.copy(error = UiText.Resource(Res.string.error_nip_invalid)) }
            return
        }
        if (token.isBlank()) {
            _uiState.update { it.copy(error = UiText.Resource(Res.string.error_token_required)) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            setEnvironmentInteractor(state.environment)
            initSessionInteractor(InitSessionInteractor.Params(nip = nip, ksefToken = token))
                .onSuccess {
                    persistCredentialsInteractor(
                        PersistCredentialsInteractor.Params(
                            nip = nip,
                            token = token,
                            environment = state.environment,
                            remember = state.rememberCredentials,
                            requireBiometrics =
                                state.requireBiometrics && state.biometricsAvailable,
                        )
                    )
                    _uiState.update { it.copy(isLoading = false) }
                    eventChannel.send(LoginEvent.LoginSuccess)
                }
                .onFailure { e ->
                    KsefLogger.e(e) { "Login failed" }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error =
                                e.message?.let { msg -> UiText.Raw(msg) }
                                    ?: UiText.Resource(Res.string.error_login_failed),
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
