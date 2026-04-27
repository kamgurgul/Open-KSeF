package com.kgurgul.openksef.domain.model

enum class KsefEnvironment(val baseUrl: String) {
    TEST("https://api-test.ksef.mf.gov.pl/v2"),
    DEMO("https://api-ksef-demo.mf.gov.pl/v2"),
    PRODUCTION("https://api.ksef.mf.gov.pl/v2")
}

data class SessionInfo(
    val accessToken: String,
    val referenceNumber: String,
    val nip: String
)

data class InvoiceSummary(
    val ksefReferenceNumber: String,
    val invoiceNumber: String,
    val invoicingDate: String,
    val sellerNip: String,
    val sellerName: String,
    val buyerNip: String,
    val buyerName: String,
    val net: String,
    val vat: String,
    val gross: String
)

data class InvoiceListResult(
    val items: List<InvoiceSummary>,
    val totalCount: Int,
    val hasMore: Boolean
)

data class SendInvoiceResult(
    val referenceNumber: String
)
