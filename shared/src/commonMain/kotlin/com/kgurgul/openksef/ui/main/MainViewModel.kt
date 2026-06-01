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

package com.kgurgul.openksef.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.data.SessionEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/** Root-screen state. [sessionExpired] stays `true` until the navigation host resets the stack. */
data class MainUiState(val sessionExpired: Boolean = false)

/**
 * Root-screen [ViewModel]. It owns the single, app-wide reaction to session expiry: it observes
 * [SessionEventBus] and raises [MainUiState.sessionExpired]. The flag is exposed as retained state
 * (not a one-shot event) so the redirect cannot be missed if the navigation host is recomposing or
 * its lifecycle is briefly stopped when the session expires — the host always converges to the
 * login screen and then calls [onSessionExpiryHandled].
 */
class MainViewModel(sessionEventBus: SessionEventBus) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        sessionEventBus.sessionExpired
            .onEach { _uiState.update { it.copy(sessionExpired = true) } }
            .launchIn(viewModelScope)
    }

    /** Acknowledges the redirect once the navigation host has shown the login screen. */
    fun onSessionExpiryHandled() {
        _uiState.update { it.copy(sessionExpired = false) }
    }
}
