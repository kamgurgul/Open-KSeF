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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import openksef.shared.generated.resources.action_download_pdf
import openksef.shared.generated.resources.action_export_pdf
import openksef.shared.generated.resources.action_retry
import openksef.shared.generated.resources.invoice_detail_tab_pdf
import openksef.shared.generated.resources.invoice_detail_tab_xml
import openksef.shared.generated.resources.invoice_detail_xml_title
import openksef.shared.generated.resources.pdf_export_success
import openksef.shared.generated.resources.pdf_preview_error
import openksef.shared.generated.resources.pdf_saved_success
import org.jetbrains.compose.resources.stringResource

private const val PDF_TAB = 0
private const val XML_TAB = 1

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

            InvoiceDetailEvent.PdfSaved ->
                pendingMessage = UiText.Resource(Res.string.pdf_saved_success)
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
        onDownloadClick = viewModel::onDownloadClick,
        onRetryPdfClick = viewModel::onRetryPdfClick,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    uiState: InvoiceDetailUiState,
    onNavigateBack: () -> Unit,
    onExportPdfClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onRetryPdfClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var selectedTab by rememberSaveable { mutableStateOf(PDF_TAB) }

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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            )
        },
        bottomBar = {
            val showDownload =
                uiState.canPreviewPdf &&
                    uiState.invoiceXml != null &&
                    selectedTab == PDF_TAB &&
                    uiState.pdfBytes != null
            val showExport =
                !uiState.canPreviewPdf && uiState.invoiceXml != null && uiState.canExportPdf
            when {
                showDownload ->
                    BottomActionBar(
                        text = stringResource(Res.string.action_download_pdf),
                        icon = Icons.Default.Download,
                        loading = uiState.isDownloading,
                        enabled = uiState.canDownload,
                        onClick = onDownloadClick,
                    )

                showExport ->
                    BottomActionBar(
                        text = stringResource(Res.string.action_export_pdf),
                        icon = Icons.Default.PictureAsPdf,
                        loading = uiState.isExportingPdf,
                        enabled = !uiState.isExportingPdf,
                        onClick = onExportPdfClick,
                    )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                uiState.invoiceXml == null -> Unit

                uiState.canPreviewPdf ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        PrimaryTabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == PDF_TAB,
                                onClick = { selectedTab = PDF_TAB },
                                text = {
                                    Text(stringResource(Res.string.invoice_detail_tab_pdf))
                                },
                            )
                            Tab(
                                selected = selectedTab == XML_TAB,
                                onClick = { selectedTab = XML_TAB },
                                text = {
                                    Text(stringResource(Res.string.invoice_detail_tab_xml))
                                },
                            )
                        }
                        when (selectedTab) {
                            PDF_TAB ->
                                PdfTabContent(
                                    uiState = uiState,
                                    onRetryPdfClick = onRetryPdfClick,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            else ->
                                XmlContent(
                                    invoiceXml = uiState.invoiceXml,
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                )
                        }
                    }

                else ->
                    XmlContent(
                        invoiceXml = uiState.invoiceXml,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
            }
        }
    }
}

@Composable
private fun PdfTabContent(
    uiState: InvoiceDetailUiState,
    onRetryPdfClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            uiState.isPdfLoading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            uiState.pdfBytes != null ->
                PdfDocumentView(pdfBytes = uiState.pdfBytes, modifier = Modifier.fillMaxSize())

            else ->
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.pdf_preview_error),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onRetryPdfClick) {
                        Text(text = stringResource(Res.string.action_retry))
                    }
                }
        }
    }
}

@Composable
private fun XmlContent(invoiceXml: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                        text = invoiceXml,
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

@Composable
private fun BottomActionBar(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text)
            }
        }
    }
}
