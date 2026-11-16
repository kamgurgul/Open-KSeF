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

package com.kgurgul.openksef.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.domain.invoke
import com.kgurgul.openksef.domain.model.KsefEnvironment
import com.kgurgul.openksef.domain.result.GetSessionEnvironmentInteractor
import com.kgurgul.openksef.domain.result.GetSessionNipInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * @property nip NIP of the signed-in context, empty when unknown
 * @property environment KSeF environment the session is bound to, null when unknown
 */
data class SettingsUiState(val nip: String = "", val environment: KsefEnvironment? = null)

class SettingsViewModel(
    getSessionNipInteractor: GetSessionNipInteractor,
    getSessionEnvironmentInteractor: GetSessionEnvironmentInteractor,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = flow {
        emit(
            SettingsUiState(
                nip = getSessionNipInteractor().orEmpty(),
                environment = getSessionEnvironmentInteractor(),
            )
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())
}
