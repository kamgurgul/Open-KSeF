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

import com.kgurgul.openksef.domain.money.Money

enum class KsefEnvironment(val baseUrl: String) {
    TEST("https://api-test.ksef.mf.gov.pl/v2"),
    DEMO("https://api-ksef-demo.mf.gov.pl/v2"),
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
)

data class InvoiceListResult(
    val items: List<InvoiceSummary>,
    val totalCount: Int,
    val hasMore: Boolean,
)

enum class InvoiceSubjectType(val apiValue: String) {
    ISSUED("Subject1"),
    RECEIVED("Subject2"),
}

data class SendInvoiceResult(val referenceNumber: String)
