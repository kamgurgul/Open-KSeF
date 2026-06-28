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

package com.kgurgul.openksef.domain.pdf

/** KSeF structured-invoice schema variant detected from the source XML. */
enum class InvoiceSchemaType {
    FA2,
    FA3,
    FA_RR,
}

/**
 * A platform-neutral, presentation-oriented view of a KSeF invoice extracted from the raw FA XML.
 *
 * Only the fields required to render a readable invoice PDF are kept here. The desktop renderer
 * ([com.kgurgul.openksef.domain.pdf.InvoicePdfExporter]) feeds the raw XML straight to Apache FOP,
 * while the Android renderer draws this model onto a `PdfDocument` canvas.
 */
data class InvoiceDocument(
    val schema: InvoiceSchemaType,
    val invoiceNumber: String,
    val issueDate: String,
    /** Date of supply / completion of service (`P_6`); blank when it equals the issue date. */
    val saleDate: String = "",
    val currency: String,
    val seller: InvoiceDocumentParty,
    val buyer: InvoiceDocumentParty,
    val items: List<InvoiceDocumentLine>,
    /** Net/VAT/gross totals broken down by tax rate, as required by art. 106e of the VAT Act. */
    val vatSummary: List<InvoiceVatRateSummary> = emptyList(),
    val totalNet: String,
    val totalVat: String,
    val totalGross: String,
    val payment: InvoicePaymentInfo? = null,
    val annotations: List<String> = emptyList(),
    val additionalDescriptions: List<InvoiceKeyValue> = emptyList(),
    val footerLines: List<String> = emptyList(),
)

/**
 * Sales totals for a single VAT rate. The [rate] is the raw rate token from the invoice line
 * (`P_12`), e.g. `"23"`, `"8"`, `"0"`, `"zw"`. All monetary values are already-formatted strings.
 */
data class InvoiceVatRateSummary(
    val rate: String,
    val net: String,
    val vat: String,
    val gross: String,
)

/** Identification and address data of an invoice party (`Podmiot1`/`Podmiot2`). */
data class InvoiceDocumentParty(val name: String, val taxId: String, val address: String)

/** A free-form key/value pair, e.g. an entry of a `DodatkowyOpis` element. */
data class InvoiceKeyValue(val key: String, val value: String)

/** Payment summary parsed from the `Platnosc` element. */
data class InvoicePaymentInfo(
    val isPaid: Boolean,
    val paymentDate: String,
    val dueDate: String,
    val method: String,
    val bankAccount: String,
    val bankName: String,
)

/** A single invoice row (`FaWiersz`). All monetary values are kept as already-formatted strings. */
data class InvoiceDocumentLine(
    val number: String,
    val name: String,
    val unit: String,
    val quantity: String,
    val unitNetPrice: String,
    val netValue: String,
    val vatRate: String,
)
