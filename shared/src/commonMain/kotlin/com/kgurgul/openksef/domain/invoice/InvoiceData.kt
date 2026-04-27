package com.kgurgul.openksef.domain.invoice

data class InvoiceData(
    val invoiceNumber: String,
    val issueDate: String,
    val sellerNip: String,
    val sellerName: String,
    val sellerAddress: String = "",
    val buyerNip: String,
    val buyerName: String,
    val buyerAddress: String = "",
    val currency: String = "PLN",
    val items: List<InvoiceLineItem>
)

data class InvoiceLineItem(
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val vatRate: Int,
    val netValue: Double,
    val grossValue: Double
)
