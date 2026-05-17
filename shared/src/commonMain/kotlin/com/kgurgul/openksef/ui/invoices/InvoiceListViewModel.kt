package com.kgurgul.openksef.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.model.InvoiceSubjectType
import com.kgurgul.openksef.domain.model.InvoiceSummary
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
import kotlin.time.Clock
import kotlinx.datetime.todayIn

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
    data class ShowError(val message: String) : InvoiceListEvent
    data object SessionEnded : InvoiceListEvent
}

class InvoiceListViewModel(
    private val repository: KsefRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<InvoiceListUiState> = combine(_uiState, _searchQuery) { state, query ->
        state.copy(
            searchQuery = query,
            displayedInvoices = filterInvoices(state.invoices, query),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoiceListUiState())

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
            repository.getInvoices(
                dateFrom = state.dateFrom,
                dateTo = state.dateTo,
                pageSize = PAGE_SIZE,
                pageOffset = nextPage * PAGE_SIZE,
                subjectType = state.subjectType,
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
                    eventChannel.send(InvoiceListEvent.ShowError(e.message ?: GENERIC_LOAD_ERROR))
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
            repository.closeSession()
            eventChannel.send(InvoiceListEvent.SessionEnded)
        }
    }

    private fun load() {
        _uiState.update {
            it.copy(isLoading = true, currentPage = 0, invoices = emptyList())
        }

        viewModelScope.launch {
            val state = _uiState.value
            repository.getInvoices(
                dateFrom = state.dateFrom,
                dateTo = state.dateTo,
                pageSize = PAGE_SIZE,
                pageOffset = 0,
                subjectType = state.subjectType,
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
                    eventChannel.send(InvoiceListEvent.ShowError(e.message ?: GENERIC_LOAD_ERROR))
                }
        }
    }

    private fun initialState(): InvoiceListUiState {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val thirtyDaysAgo = today.minus(30, DateTimeUnit.DAY)
        return InvoiceListUiState(
            dateFrom = thirtyDaysAgo.toString(),
            dateTo = today.toString(),
        )
    }

    companion object {
        private const val PAGE_SIZE = 10
        private const val GENERIC_LOAD_ERROR = "Błąd pobierania faktur"

        internal fun filterInvoices(
            invoices: List<InvoiceSummary>,
            query: String,
        ): List<InvoiceSummary> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return invoices
            return invoices.filter { invoice ->
                invoice.matches(trimmed)
            }
        }

        private fun InvoiceSummary.matches(query: String): Boolean {
            val fields = listOf(
                invoiceNumber,
                invoicingDate,
                sellerNip,
                sellerName,
                buyerNip,
                buyerName,
                net,
                vat,
                gross,
            )
            return fields.any { it.contains(query, ignoreCase = true) }
        }
    }
}
