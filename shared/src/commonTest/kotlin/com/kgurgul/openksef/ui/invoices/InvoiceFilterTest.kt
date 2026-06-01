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

package com.kgurgul.openksef.ui.invoices

import com.kgurgul.openksef.domain.model.InvoiceSummary
import com.kgurgul.openksef.domain.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvoiceFilterTest {

    private val invoiceA =
        InvoiceSummary(
            ksefReferenceNumber = "KSEF-AAA-001",
            invoiceNumber = "FV/2024/001",
            invoicingDate = "2024-01-15T00:00:00Z",
            sellerNip = "1111111111",
            sellerName = "Alpha Sp. z o.o.",
            buyerNip = "2222222222",
            buyerName = "Beta Sp. z o.o.",
            net = Money.fromMajorUnits(1000),
            vat = Money.fromMajorUnits(230),
            gross = Money.fromMajorUnits(1230),
        )

    private val invoiceB =
        InvoiceSummary(
            ksefReferenceNumber = "KSEF-BBB-002",
            invoiceNumber = "FV/2024/002",
            invoicingDate = "2024-02-20T00:00:00Z",
            sellerNip = "3333333333",
            sellerName = "Gamma S.A.",
            buyerNip = "4444444444",
            buyerName = "Delta Sp. j.",
            net = Money.fromMajorUnits(500),
            vat = Money.fromMajorUnits(115),
            gross = Money.fromMajorUnits(615),
        )

    private val invoiceC =
        InvoiceSummary(
            ksefReferenceNumber = "KSEF-CCC-003",
            invoiceNumber = "FV/2024/003",
            invoicingDate = "2024-03-10T00:00:00Z",
            sellerNip = "5555555555",
            sellerName = "Alpha Plus",
            buyerNip = "6666666666",
            buyerName = "Epsilon",
            net = Money.fromMajorUnits(200),
            vat = Money.fromMajorUnits(46),
            gross = Money.fromMajorUnits(246),
        )

    private val all = listOf(invoiceA, invoiceB, invoiceC)

    @Test
    fun emptyQuery_returnsAll() {
        val result = InvoiceListViewModel.filterInvoices(all, "")
        assertEquals(all, result)
    }

    @Test
    fun blankQuery_returnsAll() {
        val result = InvoiceListViewModel.filterInvoices(all, "   ")
        assertEquals(all, result)
    }

    @Test
    fun filtersOnInvoiceNumber() {
        val result = InvoiceListViewModel.filterInvoices(all, "FV/2024/002")
        assertEquals(listOf(invoiceB), result)
    }

    @Test
    fun filtersOnSellerName_caseInsensitive() {
        val result = InvoiceListViewModel.filterInvoices(all, "alpha")
        assertEquals(listOf(invoiceA, invoiceC), result)
    }

    @Test
    fun filtersOnBuyerName() {
        val result = InvoiceListViewModel.filterInvoices(all, "Epsilon")
        assertEquals(listOf(invoiceC), result)
    }

    @Test
    fun filtersOnSellerNip() {
        val result = InvoiceListViewModel.filterInvoices(all, "3333333333")
        assertEquals(listOf(invoiceB), result)
    }

    @Test
    fun filtersOnBuyerNip() {
        val result = InvoiceListViewModel.filterInvoices(all, "2222222222")
        assertEquals(listOf(invoiceA), result)
    }

    @Test
    fun filtersOnGrossAmount() {
        val result = InvoiceListViewModel.filterInvoices(all, "1230.00")
        assertEquals(listOf(invoiceA), result)
    }

    @Test
    fun filtersOnNetAmount() {
        val result = InvoiceListViewModel.filterInvoices(all, "500.00")
        assertEquals(listOf(invoiceB), result)
    }

    @Test
    fun filtersOnVatAmount() {
        val result = InvoiceListViewModel.filterInvoices(all, "46.00")
        assertEquals(listOf(invoiceC), result)
    }

    @Test
    fun filtersOnInvoicingDate_partialMatch() {
        val result = InvoiceListViewModel.filterInvoices(all, "2024-02")
        assertEquals(listOf(invoiceB), result)
    }

    @Test
    fun queryTrimmed_beforeMatching() {
        val result = InvoiceListViewModel.filterInvoices(all, "  Gamma  ")
        assertEquals(listOf(invoiceB), result)
    }

    @Test
    fun noMatch_returnsEmpty() {
        val result = InvoiceListViewModel.filterInvoices(all, "nothing-matches-this")
        assertTrue(result.isEmpty())
    }

    @Test
    fun partialSubstringMatch_acrossFields() {
        // "Sp." appears in multiple buyer/seller names — A (both), B (buyer)
        val result = InvoiceListViewModel.filterInvoices(all, "Sp.")
        assertEquals(listOf(invoiceA, invoiceB), result)
    }

    @Test
    fun emptyList_returnsEmpty() {
        val result = InvoiceListViewModel.filterInvoices(emptyList(), "anything")
        assertTrue(result.isEmpty())
    }
}
