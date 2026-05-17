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
import com.kgurgul.openksef.ui.components.LoadingOverlay
import com.kgurgul.openksef.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendInvoiceScreen(
    viewModel: SendInvoiceViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (uiState.isSent) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { Text("Faktura wysłana") },
            text = {
                Column {
                    Text("Faktura została wysłana do KSeF.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nr referencyjny:",
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
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wyślij fakturę") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
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
                SectionHeader("Sprzedawca")
                OutlinedTextField(
                    value = uiState.sellerNip,
                    onValueChange = {},
                    label = { Text("NIP") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.sellerName,
                    onValueChange = viewModel::onSellerNameChanged,
                    label = { Text("Nazwa") },
                    isError = uiState.validationErrors.containsKey("sellerName"),
                    supportingText = uiState.validationErrors["sellerName"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.sellerAddress,
                    onValueChange = viewModel::onSellerAddressChanged,
                    label = { Text("Adres") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buyer section
                SectionHeader("Nabywca")
                OutlinedTextField(
                    value = uiState.buyerNip,
                    onValueChange = viewModel::onBuyerNipChanged,
                    label = { Text("NIP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.validationErrors.containsKey("buyerNip"),
                    supportingText = uiState.validationErrors["buyerNip"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.buyerName,
                    onValueChange = viewModel::onBuyerNameChanged,
                    label = { Text("Nazwa") },
                    isError = uiState.validationErrors.containsKey("buyerName"),
                    supportingText = uiState.validationErrors["buyerName"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.buyerAddress,
                    onValueChange = viewModel::onBuyerAddressChanged,
                    label = { Text("Adres") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Invoice details
                SectionHeader("Dane faktury")
                OutlinedTextField(
                    value = uiState.invoiceNumber,
                    onValueChange = viewModel::onInvoiceNumberChanged,
                    label = { Text("Numer faktury") },
                    isError = uiState.validationErrors.containsKey("invoiceNumber"),
                    supportingText = uiState.validationErrors["invoiceNumber"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.issueDate,
                    onValueChange = viewModel::onIssueDateChanged,
                    label = { Text("Data wystawienia (RRRR-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Line items
                SectionHeader("Pozycje faktury")

                if (uiState.validationErrors.containsKey("items")) {
                    Text(
                        text = uiState.validationErrors["items"] ?: "",
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
                    Text("Dodaj pozycję")
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
                        Text("Wyślij do KSeF")
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
                    text = "Pozycja ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = item.description,
                onValueChange = { onUpdate(item.copy(description = it)) },
                label = { Text("Opis") },
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
                    label = { Text("Ilość") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = item.unitPrice,
                    onValueChange = { onUpdate(item.copy(unitPrice = it)) },
                    label = { Text("Cena jedn.") },
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
                        text = "Netto: ${item.netValue} PLN",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Brutto: ${item.grossValue} PLN",
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
            label = { Text("VAT") },
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
                text = "Podsumowanie",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Netto:")
                Text("${formatDisplay(totalNet)} PLN")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("VAT:")
                Text("${formatDisplay(totalVat)} PLN")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Brutto:", fontWeight = FontWeight.Bold)
                Text("${formatDisplay(totalGross)} PLN", fontWeight = FontWeight.Bold)
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
