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
import kotlin.test.assertFalse
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
                buyerTaxId = BuyerTaxId.Nip("2222222222"),
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
                            vatRate = VatRate.RATE_23,
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
        assertTrue(xml.contains("<P_18>2</P_18>"))
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
        // No payment details were given.
        assertFalse(xml.contains("<Platnosc>"))
    }

    @Test
    fun buildXml_multipleLineItems() {
        val data =
            invoice(
                listOf(
                    line("Item 1", VatRate.RATE_23, net = 100),
                    line("Item 2", VatRate.RATE_8, net = 200),
                )
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
        val xml = InvoiceBuilder.buildXml(invoice(listOf(line("Service", VatRate.RATE_23, 1000))))

        // Total net = 1000, VAT = 230, gross = 1230
        assertTrue(xml.contains("<P_13_1>1000.00</P_13_1>"))
        assertTrue(xml.contains("<P_14_1>230.00</P_14_1>"))
        assertTrue(xml.contains("<P_15>1230.00</P_15>"))
    }

    @Test
    fun buildXml_sumsEachRateIntoItsOwnFieldsInSchemaOrder() {
        val data =
            invoice(
                listOf(
                    line("EU service", VatRate.NOT_SUBJECT_EU_SERVICES, net = 50),
                    line("Book", VatRate.RATE_5, net = 40),
                    line("Food", VatRate.RATE_8, net = 200),
                    line("Service A", VatRate.RATE_23, net = 100),
                    line("Service B", VatRate.RATE_23, net = 300),
                )
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertInOrder(
            xml,
            "<P_13_1>400.00</P_13_1>",
            "<P_14_1>92.00</P_14_1>",
            "<P_13_2>200.00</P_13_2>",
            "<P_14_2>16.00</P_14_2>",
            "<P_13_3>40.00</P_13_3>",
            "<P_14_3>2.00</P_14_3>",
            "<P_13_9>50.00</P_13_9>",
            // 400 + 92 + 200 + 16 + 40 + 2 + 50
            "<P_15>800.00</P_15>",
            "<Adnotacje>",
        )
    }

    @Test
    fun buildXml_zeroRatesUseTheirCodesAndNetOnlyFields() {
        val data =
            invoice(
                listOf(
                    line("Domestic", VatRate.ZERO_DOMESTIC, net = 10),
                    line("WDT", VatRate.ZERO_INTRA_EU, net = 20),
                    line("Export", VatRate.ZERO_EXPORT, net = 30),
                    line("Abroad", VatRate.NOT_SUBJECT_ABROAD, net = 40),
                    line("Reverse", VatRate.DOMESTIC_REVERSE_CHARGE, net = 50),
                )
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<P_12>0 KR</P_12>"))
        assertTrue(xml.contains("<P_12>0 WDT</P_12>"))
        assertTrue(xml.contains("<P_12>0 EX</P_12>"))
        assertTrue(xml.contains("<P_12>np I</P_12>"))
        assertTrue(xml.contains("<P_12>oo</P_12>"))
        assertInOrder(
            xml,
            "<P_13_6_1>10.00</P_13_6_1>",
            "<P_13_6_2>20.00</P_13_6_2>",
            "<P_13_6_3>30.00</P_13_6_3>",
            "<P_13_8>40.00</P_13_8>",
            "<P_13_10>50.00</P_13_10>",
            "<P_15>150.00</P_15>",
        )
        assertFalse(xml.contains("<P_14_"))
        assertFalse(xml.contains("<P_13_1>"))
    }

    /** The AdMob invoice: B2B service for an Irish company, paid by transfer on the issue day. */
    @Test
    fun buildXml_euServiceInvoice_matchesReverseChargeNpIIInvoice() {
        val data =
            InvoiceData(
                invoiceNumber = "FV/10/2026",
                issueDate = "2026-10-21",
                sellerNip = "1111111111",
                sellerName = "Jan Kowalski",
                sellerAddress = "ul. Rynek 1, 33-170 Tuchów",
                buyerTaxId = BuyerTaxId.EuVat(countryCode = "IE", number = "6388047V"),
                buyerName = "Google Ireland Limited",
                buyerAddress = "Gordon House, Barrow Street, Dublin 4",
                items =
                    listOf(
                        InvoiceLineItem(
                            description = "Google AdMob",
                            quantity = 1.0,
                            unit = "szt.",
                            unitPrice = Money.fromMajorUnits(307, 10),
                            vatRate = VatRate.NOT_SUBJECT_EU_SERVICES,
                            netValue = Money.fromMajorUnits(307, 10),
                            grossValue = Money.fromMajorUnits(307, 10),
                        )
                    ),
                payment =
                    InvoicePayment(
                        status = PaymentStatus.Paid(date = "2026-10-21"),
                        dueDate = "2026-10-21",
                        method = PaymentMethod.TRANSFER,
                        bankAccount =
                            BankAccount(
                                number = "61109010140000071219812874",
                                swift = "WBKPPLPP",
                                bankName = "Santander Bank Polska",
                            ),
                    ),
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertInOrder(
            xml,
            "<Podmiot2>",
            "<KodUE>IE</KodUE>",
            "<NrVatUE>6388047V</NrVatUE>",
            "<Nazwa>Google Ireland Limited</Nazwa>",
            "<KodKraju>IE</KodKraju>",
            "</Podmiot2>",
        )
        assertFalse(xml.contains("<NIP>6388047V</NIP>"))
        assertTrue(xml.contains("<P_12>np II</P_12>"))
        assertTrue(xml.contains("<P_13_9>307.10</P_13_9>"))
        assertTrue(xml.contains("<P_15>307.10</P_15>"))
        assertFalse(xml.contains("<P_13_1>"))
        assertFalse(xml.contains("<P_14_1>"))
        // np II services are settled by the buyer: "Odwrotne obciążenie".
        assertTrue(xml.contains("<P_18>1</P_18>"))
        assertInOrder(
            xml,
            "</FaWiersz>",
            "<Platnosc>",
            "<Zaplacono>1</Zaplacono>",
            "<DataZaplaty>2026-10-21</DataZaplaty>",
            "<TerminPlatnosci>",
            "<Termin>2026-10-21</Termin>",
            "<FormaPlatnosci>6</FormaPlatnosci>",
            "<RachunekBankowy>",
            "<NrRB>61109010140000071219812874</NrRB>",
            "<SWIFT>WBKPPLPP</SWIFT>",
            "<NazwaBanku>Santander Bank Polska</NazwaBanku>",
            "</RachunekBankowy>",
            "</Platnosc>",
            "</Fa>",
        )
    }

    @Test
    fun buildXml_greekBuyer_usesElForVatAndGrForAddress() {
        val data =
            invoice(listOf(line("Service", VatRate.NOT_SUBJECT_EU_SERVICES, 100)))
                .copy(buyerTaxId = BuyerTaxId.EuVat("EL", "123456789"), buyerAddress = "Athens")

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<KodUE>EL</KodUE>"))
        assertTrue(xml.contains("<KodKraju>GR</KodKraju>"))
    }

    @Test
    fun buildXml_reverseChargeFlag_setsP18WithoutRequiringRate() {
        val data =
            invoice(listOf(line("Consulting", VatRate.NOT_SUBJECT_ABROAD, 100)))
                .copy(reverseCharge = true)

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<P_18>1</P_18>"))
    }

    @Test
    fun buildXml_partialPayment_writesAmountAndDate() {
        val data =
            invoice(listOf(line("Service", VatRate.RATE_23, 100)))
                .copy(
                    payment =
                        InvoicePayment(
                            status =
                                PaymentStatus.PartiallyPaid(
                                    amount = Money.fromMajorUnits(50, 5),
                                    date = "2026-11-02",
                                )
                        )
                )

        val xml = InvoiceBuilder.buildXml(data)

        assertInOrder(
            xml,
            "<Platnosc>",
            "<ZnacznikZaplatyCzesciowej>1</ZnacznikZaplatyCzesciowej>",
            "<ZaplataCzesciowa>",
            "<KwotaZaplatyCzesciowej>50.05</KwotaZaplatyCzesciowej>",
            "<DataZaplatyCzesciowej>2026-11-02</DataZaplatyCzesciowej>",
            "</ZaplataCzesciowa>",
            "</Platnosc>",
        )
        assertFalse(xml.contains("<Zaplacono>"))
        assertFalse(xml.contains("<FormaPlatnosci>"))
    }

    @Test
    fun buildXml_bankAccountWithoutOptionalFields_writesOnlyNumber() {
        val data =
            invoice(listOf(line("Service", VatRate.RATE_23, 100)))
                .copy(
                    payment =
                        InvoicePayment(
                            method = PaymentMethod.TRANSFER,
                            bankAccount = BankAccount(number = "61109010140000071219812874"),
                        )
                )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<NrRB>61109010140000071219812874</NrRB>"))
        assertFalse(xml.contains("<SWIFT>"))
        assertFalse(xml.contains("<NazwaBanku>"))
        assertFalse(xml.contains("<Zaplacono>"))
        assertFalse(xml.contains("<TerminPlatnosci>"))
    }

    @Test
    fun buildXml_escapesXmlSpecialCharacters() {
        val data =
            InvoiceData(
                invoiceNumber = "FV/2024/004",
                issueDate = "2024-04-01",
                sellerNip = "1111111111",
                sellerName = "Firma A&B <Corp>",
                buyerTaxId = BuyerTaxId.Nip("2222222222"),
                buyerName = "Buyer \"Special\"",
                items =
                    listOf(
                        InvoiceLineItem(
                            "Item's <special>",
                            1.0,
                            "szt.",
                            Money.fromMajorUnits(100),
                            VatRate.RATE_23,
                            Money.fromMajorUnits(100),
                            Money.fromMajorUnits(123),
                        )
                    ),
                payment =
                    InvoicePayment(
                        method = PaymentMethod.TRANSFER,
                        bankAccount =
                            BankAccount(number = "61109010140000071219812874", bankName = "A&B"),
                    ),
            )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("Firma A&amp;B &lt;Corp&gt;"))
        assertTrue(xml.contains("Buyer &quot;Special&quot;"))
        assertTrue(xml.contains("Item&apos;s &lt;special&gt;"))
        assertTrue(xml.contains("<NazwaBanku>A&amp;B</NazwaBanku>"))
    }

    private fun invoice(items: List<InvoiceLineItem>) =
        InvoiceData(
            invoiceNumber = "FV/2024/002",
            issueDate = "2024-02-01",
            sellerNip = "1111111111",
            sellerName = "Seller",
            sellerAddress = "ul. Testowa 1",
            buyerTaxId = BuyerTaxId.Nip("2222222222"),
            buyerName = "Buyer",
            items = items,
        )

    private fun line(description: String, rate: VatRate, net: Long): InvoiceLineItem {
        val netValue = Money.fromMajorUnits(net)
        return InvoiceLineItem(
            description = description,
            quantity = 1.0,
            unitPrice = netValue,
            vatRate = rate,
            netValue = netValue,
            grossValue = netValue.withVatRate(rate.percent),
        )
    }

    /** Asserts that every fragment occurs in [xml], each after the previous one. */
    private fun assertInOrder(xml: String, vararg fragments: String) {
        var from = 0
        for (fragment in fragments) {
            val index = xml.indexOf(fragment, from)
            assertTrue(index >= 0, "Expected \"$fragment\" after index $from in:\n$xml")
            from = index + fragment.length
        }
    }
}
