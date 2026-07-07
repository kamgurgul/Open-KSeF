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
import kotlin.test.Test
import kotlin.test.assertTrue

class InvoiceBuilderTest {

    @Test
    fun buildXml_generatesValidFA3Structure() {
        val data =
            InvoiceData(
                invoiceNumber = "FV/2024/001",
                issueDate = "2024-01-15",
                sellerNip = "1111111111",
                sellerName = "Firma ABC",
                sellerAddress = "ul. Testowa 1, Warszawa",
                buyerNip = "2222222222",
                buyerName = "Firma XYZ",
                buyerAddress = "ul. Próbna 2, Kraków",
                currency = "PLN",
                items =
                    listOf(
                        InvoiceLineItem(
                            description = "Usługa programistyczna",
                            quantity = 10.0,
                            unit = "godz.",
                            unitPrice = Money.fromMajorUnits(100),
                            vatRate = 23,
                            netValue = Money.fromMajorUnits(1000),
                            grossValue = Money.fromMajorUnits(1230),
                        )
                    ),
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<?xml version=\"1.0\""))
        assertTrue(xml.contains("<P_8A>godz.</P_8A>"))
        assertTrue(xml.contains("<Faktura xmlns=\"http://crd.gov.pl/wzor/2025/06/25/13775/\">"))
        assertTrue(xml.contains("FA (3)"))
        assertTrue(xml.contains("<WariantFormularza>3</WariantFormularza>"))
        assertTrue(xml.contains("<P_19N>1</P_19N>"))
        assertTrue(xml.contains("<P_22N>1</P_22N>"))
        assertTrue(xml.contains("<P_23>2</P_23>"))
        assertTrue(xml.contains("<P_PMarzyN>1</P_PMarzyN>"))
        assertTrue(xml.contains("<RodzajFaktury>VAT</RodzajFaktury>"))
        assertTrue(xml.contains("<JST>2</JST>"))
        assertTrue(xml.contains("<GV>2</GV>"))
        assertTrue(xml.contains("<AdresL1>ul. Testowa 1, Warszawa</AdresL1>"))
        assertTrue(xml.contains("<NIP>1111111111</NIP>"))
        assertTrue(xml.contains("<NIP>2222222222</NIP>"))
        assertTrue(xml.contains("<Nazwa>Firma ABC</Nazwa>"))
        assertTrue(xml.contains("<Nazwa>Firma XYZ</Nazwa>"))
        assertTrue(xml.contains("<P_2>FV/2024/001</P_2>"))
        assertTrue(xml.contains("<P_7>Usługa programistyczna</P_7>"))
        assertTrue(xml.contains("<P_12>23</P_12>"))
        assertTrue(xml.contains("<KodWaluty>PLN</KodWaluty>"))
        assertTrue(xml.contains("</Faktura>"))
    }

    @Test
    fun buildXml_multipleLineItems() {
        val data =
            InvoiceData(
                invoiceNumber = "FV/2024/002",
                issueDate = "2024-02-01",
                sellerNip = "1111111111",
                sellerName = "Seller",
                buyerNip = "2222222222",
                buyerName = "Buyer",
                items =
                    listOf(
                        InvoiceLineItem(
                            "Item 1",
                            2.0,
                            "szt.",
                            Money.fromMajorUnits(50),
                            23,
                            Money.fromMajorUnits(100),
                            Money.fromMajorUnits(123),
                        ),
                        InvoiceLineItem(
                            "Item 2",
                            1.0,
                            "szt.",
                            Money.fromMajorUnits(200),
                            8,
                            Money.fromMajorUnits(200),
                            Money.fromMajorUnits(216),
                        ),
                    ),
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<NrWierszaFa>1</NrWierszaFa>"))
        assertTrue(xml.contains("<NrWierszaFa>2</NrWierszaFa>"))
        assertTrue(xml.contains("<P_7>Item 1</P_7>"))
        assertTrue(xml.contains("<P_7>Item 2</P_7>"))
        assertTrue(xml.contains("<P_12>23</P_12>"))
        assertTrue(xml.contains("<P_12>8</P_12>"))
    }

    @Test
    fun buildXml_vatCalculations() {
        val data =
            InvoiceData(
                invoiceNumber = "FV/2024/003",
                issueDate = "2024-03-01",
                sellerNip = "1111111111",
                sellerName = "Seller",
                buyerNip = "2222222222",
                buyerName = "Buyer",
                items =
                    listOf(
                        InvoiceLineItem(
                            "Service",
                            1.0,
                            "szt.",
                            Money.fromMajorUnits(1000),
                            23,
                            Money.fromMajorUnits(1000),
                            Money.fromMajorUnits(1230),
                        )
                    ),
            )

        val xml = InvoiceBuilder.buildXml(data)

        // Total net = 1000, VAT = 230, gross = 1230
        assertTrue(xml.contains("<P_13_1>1000.00</P_13_1>"))
        assertTrue(xml.contains("<P_14_1>230.00</P_14_1>"))
        assertTrue(xml.contains("<P_15>1230.00</P_15>"))
    }

    @Test
    fun buildXml_escapesXmlSpecialCharacters() {
        val data =
            InvoiceData(
                invoiceNumber = "FV/2024/004",
                issueDate = "2024-04-01",
                sellerNip = "1111111111",
                sellerName = "Firma A&B <Corp>",
                buyerNip = "2222222222",
                buyerName = "Buyer \"Special\"",
                items =
                    listOf(
                        InvoiceLineItem(
                            "Item's <special>",
                            1.0,
                            "szt.",
                            Money.fromMajorUnits(100),
                            23,
                            Money.fromMajorUnits(100),
                            Money.fromMajorUnits(123),
                        )
                    ),
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("Firma A&amp;B &lt;Corp&gt;"))
        assertTrue(xml.contains("Buyer &quot;Special&quot;"))
        assertTrue(xml.contains("Item&apos;s &lt;special&gt;"))
    }
}
