package com.kgurgul.openksef.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.model.InvoiceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlinx.datetime.todayIn

data class InvoiceListUiState(
    val invoices: List<InvoiceSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val dateFrom: String = "",
    val dateTo: String = "",
    val currentPage: Int = 0,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isSessionActive: Boolean = true
)

class InvoiceListViewModel(
    private val repository: KsefRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceListUiState())
    val uiState: StateFlow<InvoiceListUiState> = _uiState.asStateFlow()

    companion object {
        private const val PAGE_SIZE = 10
    }

    init {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val thirtyDaysAgo = today.minus(30, DateTimeUnit.DAY)
        _uiState.update {
            it.copy(
                dateFrom = thirtyDaysAgo.toString(),
                dateTo = today.toString()
            )
        }
        loadInvoices()
    }

    fun loadInvoices() {
        _uiState.update { it.copy(isLoading = true, error = null, currentPage = 0) }

        viewModelScope.launch {
            val state = _uiState.value
            repository.getInvoices(state.dateFrom, state.dateTo, PAGE_SIZE, 0)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            invoices = result.items,
                            totalCount = result.totalCount,
                            hasMore = result.hasMore,
                            isLoading = false,
                            isRefreshing = false,
                            currentPage = 0
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = e.message ?: "Błąd pobierania faktur"
                        )
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadInvoices()
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return

        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            repository.getInvoices(state.dateFrom, state.dateTo, PAGE_SIZE, nextPage * PAGE_SIZE)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            invoices = it.invoices + result.items,
                            totalCount = it.totalCount + result.totalCount,
                            hasMore = result.hasMore,
                            isLoading = false,
                            currentPage = nextPage
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Błąd pobierania faktur"
                        )
                    }
                }
        }
    }

    fun onDateFromChanged(dateFrom: String) {
        _uiState.update { it.copy(dateFrom = dateFrom) }
    }

    fun onDateToChanged(dateTo: String) {
        _uiState.update { it.copy(dateTo = dateTo) }
    }

    fun logout() {
        viewModelScope.launch {
            repository.closeSession()
            _uiState.update { it.copy(isSessionActive = false) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
