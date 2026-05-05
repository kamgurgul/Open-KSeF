package com.kgurgul.openksef.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.KsefLogger
import com.kgurgul.openksef.data.local.TokenStore
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.model.KsefEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val nip: String = "",
    val token: String = "",
    val environment: KsefEnvironment = KsefEnvironment.TEST,
    val rememberCredentials: Boolean = false,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

class LoginViewModel(
    private val repository: KsefRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        viewModelScope.launch {
            val savedNip = tokenStore.getNip().first()
            val savedToken = tokenStore.getToken().first()
            val savedEnv = tokenStore.getEnvironment().first()

            _uiState.update { state ->
                state.copy(
                    nip = savedNip ?: "",
                    token = savedToken ?: "",
                    environment = savedEnv,
                    rememberCredentials = savedNip != null
                )
            }
        }
    }

    fun onNipChanged(nip: String) {
        _uiState.update { it.copy(nip = nip, error = null) }
    }

    fun onTokenChanged(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun onEnvironmentChanged(environment: KsefEnvironment) {
        _uiState.update { it.copy(environment = environment) }
        repository.setEnvironmentBaseUrl(environment.baseUrl)
    }

    fun onRememberChanged(remember: Boolean) {
        _uiState.update { it.copy(rememberCredentials = remember) }
    }

    fun login() {
        val state = _uiState.value
        val nip = state.nip.trim()
        val token = state.token.trim()

        if (nip.length != 10 || !nip.all { it.isDigit() }) {
            _uiState.update { it.copy(error = "NIP musi mieć 10 cyfr") }
            return
        }
        if (token.isBlank()) {
            _uiState.update { it.copy(error = "Token jest wymagany") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            repository.setEnvironmentBaseUrl(state.environment.baseUrl)
            repository.initSession(nip, token)
                .onSuccess {
                    if (state.rememberCredentials) {
                        tokenStore.saveNip(nip)
                        tokenStore.saveToken(token)
                        tokenStore.saveEnvironment(state.environment)
                    } else {
                        tokenStore.clear()
                    }
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { e ->
                    KsefLogger.e(e) { "Login failed" }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Błąd logowania"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
