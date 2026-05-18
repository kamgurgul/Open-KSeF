package com.kgurgul.openksef.ui.sendinvoice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kgurgul.openksef.common.asString
import com.kgurgul.openksef.ui.components.LoadingOverlay
import com.kgurgul.openksef.ui.components.SectionHeader
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.action_back
import openksef.shared.generated.resources.amount_pln
import openksef.shared.generated.resources.action_delete
import openksef.shared.generated.resources.action_ok
import openksef.shared.generated.resources.send_invoice_add_item
import openksef.shared.generated.resources.send_invoice_address
import openksef.shared.generated.resources.send_invoice_buyer
import openksef.shared.generated.resources.send_invoice_description
import openksef.shared.generated.resources.send_invoice_details
import openksef.shared.generated.resources.send_invoice_gross_value
import openksef.shared.generated.resources.send_invoice_issue_date
import openksef.shared.generated.resources.send_invoice_item_index
import openksef.shared.generated.resources.send_invoice_items
import openksef.shared.generated.resources.send_invoice_name
import openksef.shared.generated.resources.send_invoice_net_value
import openksef.shared.generated.resources.send_invoice_number
import openksef.shared.generated.resources.send_invoice_quantity
import openksef.shared.generated.resources.send_invoice_reference_number_label
import openksef.shared.generated.resources.send_invoice_seller
import openksef.shared.generated.resources.send_invoice_send
import openksef.shared.generated.resources.send_invoice_sent_message
import openksef.shared.generated.resources.send_invoice_sent_title
import openksef.shared.generated.resources.send_invoice_summary
import openksef.shared.generated.resources.send_invoice_title
import openksef.shared.generated.resources.send_invoice_total_gross
import openksef.shared.generated.resources.send_invoice_total_net
import openksef.shared.generated.resources.send_invoice_total_vat
import openksef.shared.generated.resources.send_invoice_unit_price
import openksef.shared.generated.resources.send_invoice_vat
import openksef.shared.generated.resources.login_nip_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendInvoiceScreen(
    viewModel: SendInvoiceViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = uiState.error?.asString()
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (uiState.isSent) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { Text(stringResource(Res.string.send_invoice_sent_title)) },
            text = {
                Column {
                    Text(stringResource(Res.string.send_invoice_sent_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.send_invoice_reference_number_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = uiState.sentReferenceNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(Res.string.action_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.send_invoice_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Seller section
                SectionHeader(stringResource(Res.string.send_invoice_seller))
                OutlinedTextField(
                    value = uiState.sellerNip,
                    onValueChange = {},
                    label = { Text(stringResource(Res.string.login_nip_label)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.sellerName,
                    onValueChange = viewModel::onSellerNameChanged,
                    label = { Text(stringResource(Res.string.send_invoice_name)) },
                    isError = uiState.validationErrors.containsKey(SendInvoiceViewModel.FIELD_SELLER_NAME),
                    supportingText = uiState.validationErrors[SendInvoiceViewModel.FIELD_SELLER_NAME]
                        ?.let { { Text(it.asString()) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.sellerAddress,
                    onValueChange = viewModel::onSellerAddressChanged,
                    label = { Text(stringResource(Res.string.send_invoice_address)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buyer section
                SectionHeader(stringResource(Res.string.send_invoice_buyer))
                OutlinedTextField(
                    value = uiState.buyerNip,
                    onValueChange = viewModel::onBuyerNipChanged,
                    label = { Text(stringResource(Res.string.login_nip_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.validationErrors.containsKey(SendInvoiceViewModel.FIELD_BUYER_NIP),
                    supportingText = uiState.validationErrors[SendInvoiceViewModel.FIELD_BUYER_NIP]
                        ?.let { { Text(it.asString()) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.buyerName,
                    onValueChange = viewModel::onBuyerNameChanged,
                    label = { Text(stringResource(Res.string.send_invoice_name)) },
                    isError = uiState.validationErrors.containsKey(SendInvoiceViewModel.FIELD_BUYER_NAME),
                    supportingText = uiState.validationErrors[SendInvoiceViewModel.FIELD_BUYER_NAME]
                        ?.let { { Text(it.asString()) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.buyerAddress,
                    onValueChange = viewModel::onBuyerAddressChanged,
                    label = { Text(stringResource(Res.string.send_invoice_address)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Invoice details
                SectionHeader(stringResource(Res.string.send_invoice_details))
                OutlinedTextField(
                    value = uiState.invoiceNumber,
                    onValueChange = viewModel::onInvoiceNumberChanged,
                    label = { Text(stringResource(Res.string.send_invoice_number)) },
                    isError = uiState.validationErrors.containsKey(SendInvoiceViewModel.FIELD_INVOICE_NUMBER),
                    supportingText = uiState.validationErrors[SendInvoiceViewModel.FIELD_INVOICE_NUMBER]
                        ?.let { { Text(it.asString()) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.issueDate,
                    onValueChange = viewModel::onIssueDateChanged,
                    label = { Text(stringResource(Res.string.send_invoice_issue_date)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Line items
                SectionHeader(stringResource(Res.string.send_invoice_items))

                uiState.validationErrors[SendInvoiceViewModel.FIELD_ITEMS]?.let { itemsError ->
                    Text(
                        text = itemsError.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                uiState.items.forEachIndexed { index, item ->
                    LineItemCard(
                        index = index,
                        item = item,
                        canRemove = uiState.items.size > 1,
                        onUpdate = { viewModel.updateItem(index, it) },
                        onRemove = { viewModel.removeItem(index) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = viewModel::addItem,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.send_invoice_add_item))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Totals
                TotalsCard(items = uiState.items)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = viewModel::send,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(Res.string.send_invoice_send))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            LoadingOverlay(isLoading = uiState.isLoading)
        }
    }
}

@Composable
private fun LineItemCard(
    index: Int,
    item: InvoiceLineItemUi,
    canRemove: Boolean,
    onUpdate: (InvoiceLineItemUi) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.send_invoice_item_index, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = item.description,
                onValueChange = { onUpdate(item.copy(description = it)) },
                label = { Text(stringResource(Res.string.send_invoice_description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = { onUpdate(item.copy(quantity = it)) },
                    label = { Text(stringResource(Res.string.send_invoice_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.unitPrice,
                    onValueChange = { onUpdate(item.copy(unitPrice = it)) },
                    label = { Text(stringResource(Res.string.send_invoice_unit_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VatRateSelector(
                    selectedRate = item.vatRate,
                    onRateSelected = { onUpdate(item.copy(vatRate = it)) },
                    modifier = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.send_invoice_net_value, item.netValue),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(Res.string.send_invoice_gross_value, item.grossValue),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VatRateSelector(
    selectedRate: Int,
    onRateSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rates = listOf(23, 8, 5, 0)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = "$selectedRate%",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.send_invoice_vat)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            rates.forEach { rate ->
                DropdownMenuItem(
                    text = { Text("$rate%") },
                    onClick = {
                        onRateSelected(rate)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(items: List<InvoiceLineItemUi>) {
    val totalNet = items.sumOf { it.netValue.toDoubleOrNull() ?: 0.0 }
    val totalGross = items.sumOf { it.grossValue.toDoubleOrNull() ?: 0.0 }
    val totalVat = totalGross - totalNet

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.send_invoice_summary),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(Res.string.send_invoice_total_net))
                Text(
                    stringResource(Res.string.amount_pln, formatDisplay(totalNet))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(Res.string.send_invoice_total_vat))
                Text(
                    stringResource(Res.string.amount_pln, formatDisplay(totalVat))
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(Res.string.send_invoice_total_gross),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.amount_pln, formatDisplay(totalGross)),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatDisplay(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100.0
    val str = rounded.toString()
    val dotIndex = str.indexOf('.')
    return if (dotIndex == -1) "$str.00"
    else {
        val decimals = str.length - dotIndex - 1
        when {
            decimals == 1 -> "${str}0"
            decimals >= 2 -> str.substring(0, dotIndex + 3)
            else -> str
        }
    }
}
