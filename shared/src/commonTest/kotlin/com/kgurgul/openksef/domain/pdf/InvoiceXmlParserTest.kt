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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceXmlParserTest {

    @Test
    fun parse_extractsHeaderPartiesAndItems() {
        val document = InvoiceXmlParser.parse(FA2_INVOICE)

        assertEquals(InvoiceSchemaType.FA2, document.schema)
        assertEquals("FV/2026/05/1", document.invoiceNumber)
        assertEquals("2026-05-20", document.issueDate)
        assertEquals("PLN", document.currency)

        // Entity decoding is applied to text content.
        assertEquals("Seller & Co", document.seller.name)
        assertEquals("1234567890", document.seller.taxId)
        assertEquals("ul. Testowa 1, Warszawa", document.seller.address)

        assertEquals("Buyer Sp. z o.o.", document.buyer.name)
        assertEquals("9876543210", document.buyer.taxId)
        assertEquals("", document.buyer.address)
    }

    @Test
    fun parse_extractsLineItems() {
        val document = InvoiceXmlParser.parse(FA2_INVOICE)

        assertEquals(2, document.items.size)
        val first = document.items[0]
        assertEquals("1", first.number)
        assertEquals("Widget", first.name)
        assertEquals("szt.", first.unit)
        assertEquals("2", first.quantity)
        assertEquals("100.00", first.netValue)
        assertEquals("23", first.vatRate)
    }

    @Test
    fun parse_computesTotalsFromRateBuckets() {
        val document = InvoiceXmlParser.parse(FA2_INVOICE)

        assertEquals("130.00", document.totalNet)
        assertEquals("27.40", document.totalVat)
        assertEquals("157.40", document.totalGross)
    }

    @Test
    fun parse_fallsBackToLineSumWhenRateBucketsMissing() {
        val document = InvoiceXmlParser.parse(INVOICE_WITHOUT_TOTALS)

        // No P_13_x / P_14_x: net is summed from rows, VAT derived from P_15.
        assertEquals("100.00", document.totalNet)
        assertEquals("123.00", document.totalGross)
        assertEquals("23.00", document.totalVat)
    }

    @Test
    fun parse_detectsFa3Schema() {
        val fa3 = FA2_INVOICE.replace("FA (2)", "FA (3)")

        assertEquals(InvoiceSchemaType.FA3, InvoiceXmlParser.parse(fa3).schema)
        assertEquals(InvoiceSchemaType.FA3, InvoiceXmlParser.detectSchema(fa3))
    }

    @Test
    fun parse_toleratesMinimalDocument() {
        val document = InvoiceXmlParser.parse("<Faktura></Faktura>")

        assertEquals(InvoiceSchemaType.FA2, document.schema)
        assertEquals("", document.invoiceNumber)
        assertTrue(document.items.isEmpty())
        assertEquals("0.00", document.totalGross)
    }

    @Test
    fun parse_extractsPaymentInfo() {
        val payment = InvoiceXmlParser.parse(FA2_INVOICE_FULL).payment

        assertNotNull(payment)
        assertTrue(payment.isPaid)
        assertEquals("2026-05-21", payment.paymentDate)
        assertEquals("2026-06-03", payment.dueDate)
        assertEquals("Przelew", payment.method)
        assertEquals("12345678901234567890123456", payment.bankAccount)
        assertEquals("Test Bank", payment.bankName)
    }

    @Test
    fun parse_extractsActiveAnnotationsOnly() {
        val annotations = InvoiceXmlParser.parse(FA2_INVOICE_FULL).annotations

        // P_17, P_18, P_23 are 0; Zwolnienie/NoweSrodkiTransportu/PMarzy do not apply.
        assertEquals(listOf("Metoda kasowa", "Mechanizm podzielonej płatności (MPP)"), annotations)
    }

    @Test
    fun parse_extractsAdditionalDescriptions() {
        val descriptions = InvoiceXmlParser.parse(FA2_INVOICE_FULL).additionalDescriptions

        assertEquals(2, descriptions.size)
        assertEquals(InvoiceKeyValue("Zamówienie", "ZAM/2026/05/7"), descriptions[0])
        assertEquals(InvoiceKeyValue("Poz. 1 · Gwarancja", "24 miesiące"), descriptions[1])
    }

    @Test
    fun parse_extractsFooterLines() {
        val footer = InvoiceXmlParser.parse(FA2_INVOICE_FULL).footerLines

        assertEquals(2, footer.size)
        assertEquals("Dziękujemy za współpracę.", footer[0])
        assertEquals("Seller & Co Sp. z o.o.  ·  KRS: 0000123456  ·  REGON: 123456789", footer[1])
    }

    @Test
    fun parse_minimalDocumentHasNoExtraSections() {
        val document = InvoiceXmlParser.parse("<Faktura></Faktura>")

        assertNull(document.payment)
        assertTrue(document.annotations.isEmpty())
        assertTrue(document.additionalDescriptions.isEmpty())
        assertTrue(document.footerLines.isEmpty())
    }

    private companion object {
        val FA2_INVOICE =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Faktura xmlns="http://crd.gov.pl/wzor/2023/06/29/12648/">
              <Naglowek>
                <KodFormularza kodSystemowy="FA (2)" wersjaSchemy="1-0E">FA</KodFormularza>
                <WariantFormularza>2</WariantFormularza>
                <DataWytworzeniaFa>2026-05-20T08:00:00</DataWytworzeniaFa>
              </Naglowek>
              <Podmiot1>
                <DaneIdentyfikacyjne>
                  <NIP>1234567890</NIP>
                  <Nazwa>Seller &amp; Co</Nazwa>
                </DaneIdentyfikacyjne>
                <Adres>
                  <KodKraju>PL</KodKraju>
                  <AdresL1>ul. Testowa 1, Warszawa</AdresL1>
                </Adres>
              </Podmiot1>
              <Podmiot2>
                <DaneIdentyfikacyjne>
                  <NIP>9876543210</NIP>
                  <Nazwa>Buyer Sp. z o.o.</Nazwa>
                </DaneIdentyfikacyjne>
              </Podmiot2>
              <Fa>
                <KodWaluty>PLN</KodWaluty>
                <P_1>2026-05-20</P_1>
                <P_2>FV/2026/05/1</P_2>
                <P_13_1>100.00</P_13_1>
                <P_14_1>23.00</P_14_1>
                <P_13_2>30.00</P_13_2>
                <P_14_2>4.40</P_14_2>
                <P_15>157.40</P_15>
                <FaWiersz>
                  <NrWierszaFa>1</NrWierszaFa>
                  <P_7>Widget</P_7>
                  <P_8A>szt.</P_8A>
                  <P_8B>2</P_8B>
                  <P_9A>50.00</P_9A>
                  <P_11>100.00</P_11>
                  <P_12>23</P_12>
                </FaWiersz>
                <FaWiersz>
                  <NrWierszaFa>2</NrWierszaFa>
                  <P_7>Service</P_7>
                  <P_8A>godz.</P_8A>
                  <P_8B>1</P_8B>
                  <P_9A>30.00</P_9A>
                  <P_11>30.00</P_11>
                  <P_12>8</P_12>
                </FaWiersz>
              </Fa>
            </Faktura>
            """
                .trimIndent()

        val FA2_INVOICE_FULL =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Faktura xmlns="http://crd.gov.pl/wzor/2023/06/29/12648/">
              <Naglowek>
                <KodFormularza kodSystemowy="FA (2)" wersjaSchemy="1-0E">FA</KodFormularza>
              </Naglowek>
              <Podmiot1><DaneIdentyfikacyjne><NIP>1</NIP><Nazwa>A</Nazwa></DaneIdentyfikacyjne></Podmiot1>
              <Podmiot2><DaneIdentyfikacyjne><NIP>2</NIP><Nazwa>B</Nazwa></DaneIdentyfikacyjne></Podmiot2>
              <Fa>
                <KodWaluty>PLN</KodWaluty>
                <P_1>2026-05-20</P_1>
                <P_2>FV/2026/05/9</P_2>
                <P_15>123.00</P_15>
                <Adnotacje>
                  <P_16>1</P_16>
                  <P_17>0</P_17>
                  <P_18>0</P_18>
                  <P_18A>1</P_18A>
                  <Zwolnienie><P_19N>1</P_19N></Zwolnienie>
                  <NoweSrodkiTransportu><P_22N>1</P_22N></NoweSrodkiTransportu>
                  <P_23>0</P_23>
                  <PMarzy><P_PMarzyN>1</P_PMarzyN></PMarzy>
                </Adnotacje>
                <FaWiersz><P_11>100.00</P_11></FaWiersz>
                <Platnosc>
                  <Zaplacono>1</Zaplacono>
                  <DataZaplaty>2026-05-21</DataZaplaty>
                  <FormaPlatnosci>6</FormaPlatnosci>
                  <TerminPlatnosci><Termin>2026-06-03</Termin></TerminPlatnosci>
                  <RachunekBankowy>
                    <NrRB>12345678901234567890123456</NrRB>
                    <NazwaBanku>Test Bank</NazwaBanku>
                  </RachunekBankowy>
                </Platnosc>
                <DodatkowyOpis>
                  <Klucz>Zamówienie</Klucz>
                  <Wartosc>ZAM/2026/05/7</Wartosc>
                </DodatkowyOpis>
                <DodatkowyOpis>
                  <NrWiersza>1</NrWiersza>
                  <Klucz>Gwarancja</Klucz>
                  <Wartosc>24 miesiące</Wartosc>
                </DodatkowyOpis>
              </Fa>
              <Stopka>
                <Informacje><StopkaFaktury>Dziękujemy za współpracę.</StopkaFaktury></Informacje>
                <Rejestry>
                  <PelnaNazwa>Seller &amp; Co Sp. z o.o.</PelnaNazwa>
                  <KRS>0000123456</KRS>
                  <REGON>123456789</REGON>
                </Rejestry>
              </Stopka>
            </Faktura>
            """
                .trimIndent()

        val INVOICE_WITHOUT_TOTALS =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <Faktura xmlns="http://crd.gov.pl/wzor/2023/06/29/12648/">
              <Naglowek>
                <KodFormularza kodSystemowy="FA (2)" wersjaSchemy="1-0E">FA</KodFormularza>
              </Naglowek>
              <Podmiot1><DaneIdentyfikacyjne><NIP>1</NIP><Nazwa>A</Nazwa></DaneIdentyfikacyjne></Podmiot1>
              <Podmiot2><DaneIdentyfikacyjne><NIP>2</NIP><Nazwa>B</Nazwa></DaneIdentyfikacyjne></Podmiot2>
              <Fa>
                <KodWaluty>PLN</KodWaluty>
                <P_2>1</P_2>
                <P_15>123.00</P_15>
                <FaWiersz><P_11>100.00</P_11></FaWiersz>
              </Fa>
            </Faktura>
            """
                .trimIndent()
    }
}
