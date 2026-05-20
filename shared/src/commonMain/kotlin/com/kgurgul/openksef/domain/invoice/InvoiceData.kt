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
    val items: List<InvoiceLineItem>,
)

data class InvoiceLineItem(
    val description: String,
    val quantity: Double,
    val unitPrice: Double,
    val vatRate: Int,
    val netValue: Double,
    val grossValue: Double,
)
