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

package com.kgurgul.openksef.ui.invoicedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgurgul.openksef.common.ObserveAsEvents
import com.kgurgul.openksef.common.PlatformHorizontalScrollbar
import com.kgurgul.openksef.common.PlatformVerticalScrollbar
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.common.asString
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.action_back
import openksef.shared.generated.resources.action_export_pdf
import openksef.shared.generated.resources.invoice_detail_xml_title
import openksef.shared.generated.resources.pdf_export_success
import org.jetbrains.compose.resources.stringResource

@Composable
fun InvoiceDetailScreen(viewModel: InvoiceDetailViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingMessage by remember { mutableStateOf<UiText?>(null) }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is InvoiceDetailEvent.ShowError -> pendingMessage = event.message
            InvoiceDetailEvent.PdfExported ->
                pendingMessage = UiText.Resource(Res.string.pdf_export_success)
        }
    }
    val message = pendingMessage?.asString()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            pendingMessage = null
        }
    }

    InvoiceDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onExportPdfClick = viewModel::onExportPdfClick,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    uiState: InvoiceDetailUiState,
    onNavigateBack: () -> Unit,
    onExportPdfClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.ksefReferenceNumber,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    if (uiState.canExportPdf && uiState.invoiceXml != null) {
                        if (uiState.isExportingPdf) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        } else {
                            IconButton(onClick = onExportPdfClick) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription =
                                        stringResource(Res.string.action_export_pdf),
                                )
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.invoiceXml != null -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = stringResource(Res.string.invoice_detail_xml_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val verticalScrollState = rememberScrollState()
                        val horizontalScrollState = rememberScrollState()
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .padding(12.dp)
                                            .verticalScroll(verticalScrollState)
                                            .horizontalScroll(horizontalScrollState)
                                ) {
                                    Text(
                                        text = uiState.invoiceXml ?: "",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                    )
                                }
                                PlatformVerticalScrollbar(verticalScrollState)
                                PlatformHorizontalScrollbar(horizontalScrollState)
                            }
                        }
                    }
                }
            }

            if (uiState.isExportingPdf) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
