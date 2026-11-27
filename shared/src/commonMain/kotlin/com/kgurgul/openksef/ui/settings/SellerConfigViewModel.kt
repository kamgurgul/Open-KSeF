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
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.domain.invoice.BankAccount
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
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_bank_account_invalid
import openksef.shared.generated.resources.error_swift_invalid

data class SellerConfigUiState(
    val name: String = "",
    val address: String = "",
    val bankAccount: String = "",
    val bankSwift: String = "",
    val bankName: String = "",
    val bankAccountError: UiText? = null,
    val bankSwiftError: UiText? = null,
)

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
    private val errors = MutableStateFlow(Errors())

    val uiState: StateFlow<SellerConfigUiState> =
        combine(edits, errors, sellerConfigObservable.observe()) { edits, errors, config ->
                SellerConfigUiState(
                    name = edits.name ?: config?.name ?: "",
                    address = edits.address ?: config?.address ?: "",
                    bankAccount = edits.bankAccount ?: config?.bankAccount ?: "",
                    bankSwift = edits.bankSwift ?: config?.bankSwift ?: "",
                    bankName = edits.bankName ?: config?.bankName ?: "",
                    bankAccountError = errors.bankAccount,
                    bankSwiftError = errors.bankSwift,
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

    fun onBankAccountChanged(bankAccount: String) {
        edits.update { it.copy(bankAccount = bankAccount) }
        errors.update { it.copy(bankAccount = null) }
    }

    fun onBankSwiftChanged(bankSwift: String) {
        edits.update { it.copy(bankSwift = bankSwift) }
        errors.update { it.copy(bankSwift = null) }
    }

    fun onBankNameChanged(bankName: String) {
        edits.update { it.copy(bankName = bankName) }
    }

    fun onSaveClicked() {
        val state = uiState.value
        val validation =
            Errors(
                bankAccount =
                    UiText.Resource(Res.string.error_bank_account_invalid).takeIf {
                        state.bankAccount.isNotBlank() &&
                            !BankAccount.isValidNumber(state.bankAccount)
                    },
                bankSwift =
                    UiText.Resource(Res.string.error_swift_invalid).takeIf {
                        state.bankSwift.isNotBlank() && !BankAccount.isValidSwift(state.bankSwift)
                    },
            )
        errors.value = validation
        if (validation.hasAny) return

        viewModelScope.launch {
            saveSellerConfigInteractor(
                SellerConfig(
                    name = state.name.trim(),
                    address = state.address.trim(),
                    bankAccount = state.bankAccount.trim(),
                    bankSwift = BankAccount.normalizeSwift(state.bankSwift),
                    bankName = state.bankName.trim(),
                )
            )
            eventChannel.send(SellerConfigEvent.Saved)
        }
    }

    private data class Edits(
        val name: String? = null,
        val address: String? = null,
        val bankAccount: String? = null,
        val bankSwift: String? = null,
        val bankName: String? = null,
    )

    private data class Errors(val bankAccount: UiText? = null, val bankSwift: UiText? = null) {
        val hasAny: Boolean
            get() = bankAccount != null || bankSwift != null
    }
}
