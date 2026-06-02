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
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.invoice.SellerConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SellerConfigUiState(val name: String = "", val address: String = "")

sealed interface SellerConfigEvent {
    /** The seller config was persisted — the screen confirms it to the user. */
    data object Saved : SellerConfigEvent
}

class SellerConfigViewModel(private val repository: SellerConfigRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SellerConfigUiState())
    val uiState: StateFlow<SellerConfigUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SellerConfigEvent>(Channel.BUFFERED)
    val events: Flow<SellerConfigEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.config.first()?.let { config ->
                _uiState.update { it.copy(name = config.name, address = config.address) }
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onAddressChanged(address: String) {
        _uiState.update { it.copy(address = address) }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.save(SellerConfig(name = state.name.trim(), address = state.address.trim()))
            eventChannel.send(SellerConfigEvent.Saved)
        }
    }
}
