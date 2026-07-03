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

package com.kgurgul.openksef.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.domain.invoke
import com.kgurgul.openksef.domain.model.InvoiceSubjectType
import com.kgurgul.openksef.domain.model.InvoiceSummary
import com.kgurgul.openksef.domain.result.CloseSessionInteractor
import com.kgurgul.openksef.domain.result.GetInvoicesInteractor
import kotlin.time.Clock
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_load_invoices

data class InvoiceListUiState(
    val invoices: List<InvoiceSummary> = emptyList(),
    val displayedInvoices: List<InvoiceSummary> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dateFrom: String = "",
    val dateTo: String = "",
    val currentPage: Int = 0,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val subjectType: InvoiceSubjectType = InvoiceSubjectType.ISSUED,
)

sealed interface InvoiceListEvent {
    data class ShowError(val message: UiText) : InvoiceListEvent

    data object SessionEnded : InvoiceListEvent
}

class InvoiceListViewModel(
    private val getInvoicesInteractor: GetInvoicesInteractor,
    private val closeSessionInteractor: CloseSessionInteractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<InvoiceListUiState> =
        combine(_uiState, _searchQuery) { state, query ->
                state.copy(
                    searchQuery = query,
                    displayedInvoices = filterInvoices(state.invoices, query),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoiceListUiState())

    private val eventChannel = Channel<InvoiceListEvent>(Channel.BUFFERED)
    val events: Flow<InvoiceListEvent> = eventChannel.receiveAsFlow()

    init {
        load()
    }

    fun onSearchClicked() {
        load()
    }

    fun onRefreshClicked() {
        _uiState.update { it.copy(isRefreshing = true) }
        load()
    }

    fun onLoadNextPage() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return

        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            getInvoicesInteractor(
                    GetInvoicesInteractor.Params(
                        dateFrom = state.dateFrom,
                        dateTo = state.dateTo,
                        pageSize = PAGE_SIZE,
                        pageOffset = nextPage * PAGE_SIZE,
                        subjectType = state.subjectType,
                    )
                )
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            invoices = it.invoices + result.items,
                            totalCount = it.totalCount + result.totalCount,
                            hasMore = result.hasMore,
                            isLoading = false,
                            currentPage = nextPage,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false) }
                    eventChannel.send(InvoiceListEvent.ShowError(e.toUiText()))
                }
        }
    }

    fun onSubjectTypeChanged(subjectType: InvoiceSubjectType) {
        if (_uiState.value.subjectType == subjectType) return
        _uiState.update { it.copy(subjectType = subjectType) }
        load()
    }

    fun onDateFromChanged(dateFrom: String) {
        _uiState.update { it.copy(dateFrom = dateFrom) }
    }

    fun onDateToChanged(dateTo: String) {
        _uiState.update { it.copy(dateTo = dateTo) }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onLogoutClicked() {
        viewModelScope.launch {
            closeSessionInteractor()
            eventChannel.send(InvoiceListEvent.SessionEnded)
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, currentPage = 0, invoices = emptyList()) }

        viewModelScope.launch {
            val state = _uiState.value
            getInvoicesInteractor(
                    GetInvoicesInteractor.Params(
                        dateFrom = state.dateFrom,
                        dateTo = state.dateTo,
                        pageSize = PAGE_SIZE,
                        pageOffset = 0,
                        subjectType = state.subjectType,
                    )
                )
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            invoices = result.items,
                            totalCount = result.totalCount,
                            hasMore = result.hasMore,
                            isLoading = false,
                            isRefreshing = false,
                            currentPage = 0,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                    eventChannel.send(InvoiceListEvent.ShowError(e.toUiText()))
                }
        }
    }

    private fun Throwable.toUiText(): UiText =
        message?.let { UiText.Raw(it) } ?: UiText.Resource(Res.string.error_load_invoices)

    private fun initialState(): InvoiceListUiState {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val thirtyDaysAgo = today.minus(30, DateTimeUnit.DAY)
        return InvoiceListUiState(dateFrom = thirtyDaysAgo.toString(), dateTo = today.toString())
    }

    companion object {
        private const val PAGE_SIZE = 10

        internal fun filterInvoices(
            invoices: List<InvoiceSummary>,
            query: String,
        ): List<InvoiceSummary> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return invoices
            return invoices.filter { invoice -> invoice.matches(trimmed) }
        }

        private fun InvoiceSummary.matches(query: String): Boolean {
            val fields =
                listOf(
                    invoiceNumber,
                    invoicingDate,
                    sellerNip,
                    sellerName,
                    buyerNip,
                    buyerName,
                    net.toPlainString(),
                    vat.toPlainString(),
                    gross.toPlainString(),
                )
            return fields.any { it.contains(query, ignoreCase = true) }
        }
    }
}
