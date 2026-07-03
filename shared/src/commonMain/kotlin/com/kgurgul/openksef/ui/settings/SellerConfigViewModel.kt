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
import com.kgurgul.openksef.domain.invoice.SellerConfig
import com.kgurgul.openksef.domain.observable.SellerConfigObservable
import com.kgurgul.openksef.domain.observe
import com.kgurgul.openksef.domain.result.SaveSellerConfigInteractor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SellerConfigUiState(val name: String = "", val address: String = "")

sealed interface SellerConfigEvent {
    /** The seller config was persisted — the screen confirms it to the user. */
    data object Saved : SellerConfigEvent
}

class SellerConfigViewModel(
    sellerConfigObservable: SellerConfigObservable,
    private val saveSellerConfigInteractor: SaveSellerConfigInteractor,
) : ViewModel() {

    /** User edits; `null` fields fall back to the persisted config. */
    private val edits = MutableStateFlow(Edits())

    val uiState: StateFlow<SellerConfigUiState> =
        combine(edits, sellerConfigObservable.observe()) { edits, config ->
                SellerConfigUiState(
                    name = edits.name ?: config?.name ?: "",
                    address = edits.address ?: config?.address ?: "",
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellerConfigUiState())

    private val eventChannel = Channel<SellerConfigEvent>(Channel.BUFFERED)
    val events: Flow<SellerConfigEvent> = eventChannel.receiveAsFlow()

    fun onNameChanged(name: String) {
        edits.update { it.copy(name = name) }
    }

    fun onAddressChanged(address: String) {
        edits.update { it.copy(address = address) }
    }

    fun onSaveClicked() {
        val state = uiState.value
        viewModelScope.launch {
            saveSellerConfigInteractor(
                SellerConfig(name = state.name.trim(), address = state.address.trim())
            )
            eventChannel.send(SellerConfigEvent.Saved)
        }
    }

    private data class Edits(val name: String? = null, val address: String? = null)
}
