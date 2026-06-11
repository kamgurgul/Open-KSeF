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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgurgul.openksef.common.ObserveAsEvents
import com.kgurgul.openksef.common.asString
import com.kgurgul.openksef.domain.model.InvoiceSubjectType
import com.kgurgul.openksef.ui.components.InvoiceCard
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.action_cancel
import openksef.shared.generated.resources.action_clear_filter
import openksef.shared.generated.resources.action_logout
import openksef.shared.generated.resources.action_ok
import openksef.shared.generated.resources.action_refresh
import openksef.shared.generated.resources.action_search
import openksef.shared.generated.resources.action_send_invoice
import openksef.shared.generated.resources.action_settings
import openksef.shared.generated.resources.invoices_date_range
import openksef.shared.generated.resources.invoices_empty_description
import openksef.shared.generated.resources.invoices_empty_filtered_description
import openksef.shared.generated.resources.invoices_empty_filtered_title
import openksef.shared.generated.resources.invoices_empty_title
import openksef.shared.generated.resources.invoices_filter_placeholder
import openksef.shared.generated.resources.invoices_tab_issued
import openksef.shared.generated.resources.invoices_tab_received
import openksef.shared.generated.resources.invoices_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun InvoiceListScreen(
    viewModel: InvoiceListViewModel,
    onInvoiceClick: (String) -> Unit,
    onSendInvoiceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingError by remember { mutableStateOf<com.kgurgul.openksef.common.UiText?>(null) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is InvoiceListEvent.ShowError -> pendingError = event.message
            InvoiceListEvent.SessionEnded -> onLoggedOut()
        }
    }
    val errorMessage = pendingError?.asString()
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            pendingError = null
        }
    }

    InvoiceListScreen(
        uiState = uiState,
        onInvoiceClick = onInvoiceClick,
        onSendInvoiceClick = onSendInvoiceClick,
        onSettingsClick = onSettingsClick,
        onRefreshClick = viewModel::onRefreshClicked,
        onLogoutClick = viewModel::onLogoutClicked,
        onSubjectTypeChanged = viewModel::onSubjectTypeChanged,
        onDateFromChanged = viewModel::onDateFromChanged,
        onDateToChanged = viewModel::onDateToChanged,
        onSearchClick = viewModel::onSearchClicked,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onLoadNextPage = viewModel::onLoadNextPage,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    uiState: InvoiceListUiState,
    onInvoiceClick: (String) -> Unit,
    onSendInvoiceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSubjectTypeChanged: (InvoiceSubjectType) -> Unit,
    onDateFromChanged: (String) -> Unit,
    onDateToChanged: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onLoadNextPage: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    var showDateFromPicker by remember { mutableStateOf(false) }
    var showDateToPicker by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= uiState.invoices.size - 3 &&
                uiState.invoices.size < uiState.totalCount
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadNextPage()
        }
    }

    if (showDateFromPicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = uiState.dateFrom.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showDateFromPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateFromChanged(millis.toLocalDateString())
                        }
                        showDateFromPicker = false
                    }
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateFromPicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDateToPicker) {
        val datePickerState =
            rememberDatePickerState(initialSelectedDateMillis = uiState.dateTo.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showDateToPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateToChanged(millis.toLocalDateString())
                        }
                        showDateToPicker = false
                    }
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateToPicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.invoices_title)) },
                actions = {
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.action_refresh),
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(Res.string.action_settings),
                        )
                    }
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(Res.string.action_logout),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSendInvoiceClick) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(Res.string.action_send_invoice),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    )
        ) {
            val selectedTabIndex = if (uiState.subjectType == InvoiceSubjectType.ISSUED) 0 else 1
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { onSubjectTypeChanged(InvoiceSubjectType.ISSUED) },
                    text = { Text(stringResource(Res.string.invoices_tab_issued)) },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { onSubjectTypeChanged(InvoiceSubjectType.RECEIVED) },
                    text = { Text(stringResource(Res.string.invoices_tab_received)) },
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.invoices_date_range),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { showDateFromPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(uiState.dateFrom, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(" — ", modifier = Modifier.padding(horizontal = 4.dp))
                        OutlinedButton(
                            onClick = { showDateToPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(uiState.dateTo, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(onClick = onSearchClick) {
                            Text(stringResource(Res.string.action_search))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(Res.string.invoices_filter_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.action_clear_filter),
                            )
                        }
                    }
                },
                singleLine = true,
            )

            if (uiState.isLoading && uiState.invoices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.displayedInvoices.isEmpty()) {
                val isFiltered = uiState.searchQuery.isNotBlank() && uiState.invoices.isNotEmpty()
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text =
                                if (isFiltered) {
                                    stringResource(Res.string.invoices_empty_filtered_title)
                                } else {
                                    stringResource(Res.string.invoices_empty_title)
                                },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                if (isFiltered) {
                                    stringResource(
                                        Res.string.invoices_empty_filtered_description,
                                        uiState.searchQuery,
                                    )
                                } else {
                                    stringResource(Res.string.invoices_empty_description)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = padding.calculateBottomPadding() + 88.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = uiState.displayedInvoices, key = { it.ksefReferenceNumber }) {
                        invoice ->
                        InvoiceCard(
                            invoice = invoice,
                            onClick = { onInvoiceClick(invoice.ksefReferenceNumber) },
                        )
                    }

                    if (uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.toEpochMillis(): Long? =
    runCatching { LocalDate.parse(this).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
        .getOrNull()

private fun Long.toLocalDateString(): String =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date.toString()
