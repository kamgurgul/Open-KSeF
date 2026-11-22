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
import com.kgurgul.openksef.common.toUiText
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.invoke
import com.kgurgul.openksef.domain.model.InvoiceSubjectType
import com.kgurgul.openksef.domain.model.InvoiceSummary
import com.kgurgul.openksef.domain.result.CloseSessionInteractor
import com.kgurgul.openksef.domain.result.GetInvoicesInteractor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.Job
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_date_range_invalid
import openksef.shared.generated.resources.error_date_range_too_long
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
    /** Number of invoices loaded so far. */
    val totalCount: Int = 0,
    /** True when KSeF reports further invoices matching the query. */
    val hasMore: Boolean = false,
    /** True when the KSeF limit of records per query was reached; the range must be narrowed. */
    val isTruncated: Boolean = false,
    val subjectType: InvoiceSubjectType = InvoiceSubjectType.ISSUED,
) {
    /** True when a further page can be requested. */
    val canLoadMore: Boolean
        get() = hasMore && !isTruncated
}

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

    /** The running query; a new query cancels it so a stale page is never appended. */
    private var loadJob: Job? = null

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
        if (state.isLoading || !state.canLoadMore) return

        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoading = true) }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            getInvoicesInteractor(
                    GetInvoicesInteractor.Params(
                        dateFrom = state.dateFrom,
                        dateTo = state.dateTo,
                        pageSize = PAGE_SIZE,
                        pageIndex = nextPage,
                        subjectType = state.subjectType,
                    )
                )
                .onSuccess { result ->
                    _uiState.update {
                        // KSeF pages are not a stable snapshot, so drop anything already loaded.
                        val known = it.invoices.mapTo(HashSet()) { i -> i.ksefReferenceNumber }
                        val invoices =
                            it.invoices +
                                result.items.filter { i -> known.add(i.ksefReferenceNumber) }
                        it.copy(
                            invoices = invoices,
                            totalCount = invoices.size,
                            hasMore = result.hasMore,
                            isTruncated = result.isTruncated,
                            isLoading = false,
                            currentPage = nextPage,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isLoading = false) }
                    eventChannel.send(
                        InvoiceListEvent.ShowError(e.toUiText(Res.string.error_load_invoices))
                    )
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
        loadJob?.cancel()
        val rangeError = validateDateRange(_uiState.value.dateFrom, _uiState.value.dateTo)
        if (rangeError != null) {
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            eventChannel.trySend(InvoiceListEvent.ShowError(rangeError))
            return
        }
        // While refreshing the current list stays visible until the new one arrives.
        _uiState.update {
            it.copy(
                isLoading = true,
                currentPage = 0,
                invoices = if (it.isRefreshing) it.invoices else emptyList(),
                totalCount = if (it.isRefreshing) it.totalCount else 0,
            )
        }

        loadJob = viewModelScope.launch {
            val state = _uiState.value
            getInvoicesInteractor(
                    GetInvoicesInteractor.Params(
                        dateFrom = state.dateFrom,
                        dateTo = state.dateTo,
                        pageSize = PAGE_SIZE,
                        pageIndex = 0,
                        subjectType = state.subjectType,
                    )
                )
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            invoices = result.items,
                            totalCount = result.items.size,
                            hasMore = result.hasMore,
                            isTruncated = result.isTruncated,
                            isLoading = false,
                            isRefreshing = false,
                            currentPage = 0,
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                    eventChannel.send(
                        InvoiceListEvent.ShowError(e.toUiText(Res.string.error_load_invoices))
                    )
                }
        }
    }

    private fun initialState(): InvoiceListUiState {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val thirtyDaysAgo = today.minus(30, DateTimeUnit.DAY)
        return InvoiceListUiState(dateFrom = thirtyDaysAgo.toString(), dateTo = today.toString())
    }

    companion object {
        /**
         * The metadata query is limited to 20 requests per hour, so pages are large to keep the
         * number of requests low (the API allows 10..250 records per page).
         */
        internal const val PAGE_SIZE = 100

        /**
         * Checks the date range against the constraints of the KSeF metadata query. Returns the
         * error to show or null when the range is valid.
         */
        internal fun validateDateRange(dateFrom: String, dateTo: String): UiText? {
            val from = runCatching { LocalDate.parse(dateFrom) }.getOrNull()
            val to = runCatching { LocalDate.parse(dateTo) }.getOrNull()
            return when {
                from == null || to == null -> UiText.Resource(Res.string.error_date_range_invalid)
                from > to -> UiText.Resource(Res.string.error_date_range_invalid)
                from.daysUntil(to) + 1 > KsefRepository.MAX_DATE_RANGE_DAYS ->
                    UiText.Resource(
                        Res.string.error_date_range_too_long,
                        listOf(KsefRepository.MAX_DATE_RANGE_DAYS),
                    )

                else -> null
            }
        }

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
                    ksefReferenceNumber,
                    invoicingDate,
                    issueDate,
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
