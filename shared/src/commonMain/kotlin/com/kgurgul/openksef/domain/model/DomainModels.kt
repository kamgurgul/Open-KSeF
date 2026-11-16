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

package com.kgurgul.openksef.domain.model

import com.kgurgul.openksef.common.Constants.DEFAULT_CURRENCY_CODE
import com.kgurgul.openksef.domain.money.Money

enum class KsefEnvironment(val baseUrl: String) {
    TEST("https://api-test.ksef.mf.gov.pl/v2"),
    DEMO("https://api-demo.ksef.mf.gov.pl/v2"),
    PRODUCTION("https://api.ksef.mf.gov.pl/v2"),
}

data class SessionInfo(val accessToken: String, val referenceNumber: String, val nip: String)

data class InvoiceSummary(
    val ksefReferenceNumber: String,
    val invoiceNumber: String,
    val invoicingDate: String,
    val sellerNip: String,
    val sellerName: String,
    val buyerNip: String,
    val buyerName: String,
    val net: Money,
    val vat: Money,
    val gross: Money,
    /** Issue date (`yyyy-MM-dd`) printed on the invoice. */
    val issueDate: String = "",
    /** ISO 4217 code of the currency of [net] and [gross]; [vat] is always expressed in PLN. */
    val currency: String = DEFAULT_CURRENCY_CODE,
    val kind: InvoiceKind = InvoiceKind.STANDARD,
)

/** Kind of an invoice, derived from the KSeF `invoiceType`. */
enum class InvoiceKind {
    STANDARD,
    ADVANCE,
    SETTLEMENT,
    SIMPLIFIED,
    CORRECTION;

    companion object {
        fun fromApiValue(invoiceType: String?): InvoiceKind =
            when (invoiceType) {
                "Kor",
                "KorZal",
                "KorRoz",
                "KorPef",
                "KorVatRr" -> CORRECTION

                "Zal" -> ADVANCE
                "Roz" -> SETTLEMENT
                "Upr" -> SIMPLIFIED
                else -> STANDARD
            }
    }
}

/**
 * One page of invoices.
 *
 * @property hasMore true when a further page exists
 * @property isTruncated true when KSeF reached its limit of 10 000 records for one query, so
 *   further pages cannot be requested without narrowing the date range
 */
data class InvoiceListResult(
    val items: List<InvoiceSummary>,
    val hasMore: Boolean,
    val isTruncated: Boolean = false,
)

enum class InvoiceSubjectType(val apiValue: String) {
    ISSUED("Subject1"),
    RECEIVED("Subject2"),
}

data class SendInvoiceResult(val referenceNumber: String, val ksefNumber: String? = null)
