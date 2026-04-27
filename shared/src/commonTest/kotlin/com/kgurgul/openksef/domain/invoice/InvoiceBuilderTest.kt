package com.kgurgul.openksef.domain.invoice

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains

class InvoiceBuilderTest {

    @Test
    fun buildXml_generatesValidFA2Structure() {
        val data = InvoiceData(
            invoiceNumber = "FV/2024/001",
            issueDate = "2024-01-15",
            sellerNip = "1111111111",
            sellerName = "Firma ABC",
            sellerAddress = "ul. Testowa 1, Warszawa",
            buyerNip = "2222222222",
            buyerName = "Firma XYZ",
            buyerAddress = "ul. Próbna 2, Kraków",
            currency = "PLN",
            items = listOf(
                InvoiceLineItem(
                    description = "Usługa programistyczna",
                    quantity = 10.0,
                    unitPrice = 100.0,
                    vatRate = 23,
                    netValue = 1000.0,
                    grossValue = 1230.0
                )
            )
        )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("<?xml version=\"1.0\""))
        assertTrue(xml.contains("<Faktura"))
        assertTrue(xml.contains("FA (2)"))
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
        val data = InvoiceData(
            invoiceNumber = "FV/2024/002",
            issueDate = "2024-02-01",
            sellerNip = "1111111111",
            sellerName = "Seller",
            buyerNip = "2222222222",
            buyerName = "Buyer",
            items = listOf(
                InvoiceLineItem("Item 1", 2.0, 50.0, 23, 100.0, 123.0),
                InvoiceLineItem("Item 2", 1.0, 200.0, 8, 200.0, 216.0)
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
        val data = InvoiceData(
            invoiceNumber = "FV/2024/003",
            issueDate = "2024-03-01",
            sellerNip = "1111111111",
            sellerName = "Seller",
            buyerNip = "2222222222",
            buyerName = "Buyer",
            items = listOf(
                InvoiceLineItem("Service", 1.0, 1000.0, 23, 1000.0, 1230.0)
            )
        )

        val xml = InvoiceBuilder.buildXml(data)

        // Total net = 1000, VAT = 230, gross = 1230
        assertTrue(xml.contains("<P_13_1>1000.00</P_13_1>"))
        assertTrue(xml.contains("<P_14_1>230.00</P_14_1>"))
        assertTrue(xml.contains("<P_15>1230.00</P_15>"))
    }

    @Test
    fun buildXml_escapesXmlSpecialCharacters() {
        val data = InvoiceData(
            invoiceNumber = "FV/2024/004",
            issueDate = "2024-04-01",
            sellerNip = "1111111111",
            sellerName = "Firma A&B <Corp>",
            buyerNip = "2222222222",
            buyerName = "Buyer \"Special\"",
            items = listOf(
                InvoiceLineItem("Item's <special>", 1.0, 100.0, 23, 100.0, 123.0)
            )
        )

        val xml = InvoiceBuilder.buildXml(data)

        assertTrue(xml.contains("Firma A&amp;B &lt;Corp&gt;"))
        assertTrue(xml.contains("Buyer &quot;Special&quot;"))
        assertTrue(xml.contains("Item&apos;s &lt;special&gt;"))
    }
}
