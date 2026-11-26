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

import com.kgurgul.openksef.domain.money.Money

/**
 * @property reverseCharge Adds the "odwrotne obciążenie" annotation (`P_18`). Lines with a rate
 *   that [requires it][VatRate.requiresReverseCharge] add it regardless of this flag.
 */
data class InvoiceData(
    val invoiceNumber: String,
    val issueDate: String,
    val sellerNip: String,
    val sellerName: String,
    val sellerAddress: String = "",
    val buyerTaxId: BuyerTaxId,
    val buyerName: String,
    val buyerAddress: String = "",
    val currency: String = "PLN",
    val items: List<InvoiceLineItem>,
    val reverseCharge: Boolean = false,
    val payment: InvoicePayment = InvoicePayment(),
)

data class InvoiceLineItem(
    val description: String,
    val quantity: Double,
    val unit: String = "szt.",
    val unitPrice: Money,
    val vatRate: VatRate,
    val netValue: Money,
    val grossValue: Money,
)
