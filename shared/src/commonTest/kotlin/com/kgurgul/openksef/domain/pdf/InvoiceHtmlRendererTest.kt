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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvoiceHtmlRendererTest {

    @Test
    fun render_includesCoreInvoiceFields() {
        val html = InvoiceHtmlRenderer.render(fullDocument(), "KSEF-123")

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("FAKTURA"))
        assertTrue(html.contains("FV/2026/05/1"))
        assertTrue(html.contains("Numer KSeF: KSEF-123"))
        assertTrue(html.contains("Seller Co"))
        assertTrue(html.contains("Buyer Co"))
        assertTrue(html.contains("Widget"))
        assertTrue(html.contains("123.00 PLN"))
    }

    @Test
    fun render_escapesHtmlSpecialCharacters() {
        val document = fullDocument().copy(seller = InvoiceDocumentParty("A & <B>", "1", ""))

        val html = InvoiceHtmlRenderer.render(document, "")

        assertTrue(html.contains("A &amp; &lt;B&gt;"))
        assertFalse(html.contains("A & <B>"))
    }

    @Test
    fun render_includesOptionalSectionsWhenPresent() {
        val html = InvoiceHtmlRenderer.render(fullDocument(), "")

        assertTrue(html.contains("Płatność"))
        assertTrue(html.contains("Przelew"))
        assertTrue(html.contains("Adnotacje"))
        assertTrue(html.contains("Metoda kasowa"))
        assertTrue(html.contains("Dodatkowy opis"))
        assertTrue(html.contains("Zamówienie"))
        assertTrue(html.contains("Stopka faktury"))
        assertTrue(html.contains("Dziękujemy"))
    }

    @Test
    fun render_omitsEmptyOptionalSections() {
        val html = InvoiceHtmlRenderer.render(minimalDocument(), "")

        assertFalse(html.contains("Płatność"))
        assertFalse(html.contains("Adnotacje"))
        assertFalse(html.contains("Dodatkowy opis"))
        assertFalse(html.contains("Stopka faktury"))
        // The mandatory item table still renders, even when empty.
        assertTrue(html.contains("Brak pozycji"))
    }

    private fun minimalDocument(): InvoiceDocument =
        InvoiceDocument(
            schema = InvoiceSchemaType.FA2,
            invoiceNumber = "FV/2026/05/1",
            issueDate = "2026-05-20",
            currency = "PLN",
            seller = InvoiceDocumentParty("Seller Co", "1234567890", ""),
            buyer = InvoiceDocumentParty("Buyer Co", "9876543210", ""),
            items = emptyList(),
            totalNet = "0.00",
            totalVat = "0.00",
            totalGross = "0.00",
        )

    private fun fullDocument(): InvoiceDocument =
        minimalDocument()
            .copy(
                items =
                    listOf(
                        InvoiceDocumentLine(
                            number = "1",
                            name = "Widget",
                            unit = "szt.",
                            quantity = "2",
                            unitNetPrice = "50.00",
                            netValue = "100.00",
                            vatRate = "23",
                        )
                    ),
                totalNet = "100.00",
                totalVat = "23.00",
                totalGross = "123.00",
                payment =
                    InvoicePaymentInfo(
                        isPaid = true,
                        paymentDate = "2026-05-21",
                        dueDate = "2026-06-03",
                        method = "Przelew",
                        bankAccount = "12345678901234567890123456",
                        bankName = "Test Bank",
                    ),
                annotations = listOf("Metoda kasowa"),
                additionalDescriptions = listOf(InvoiceKeyValue("Zamówienie", "ZAM/2026/05/7")),
                footerLines = listOf("Dziękujemy za współpracę."),
            )
}
