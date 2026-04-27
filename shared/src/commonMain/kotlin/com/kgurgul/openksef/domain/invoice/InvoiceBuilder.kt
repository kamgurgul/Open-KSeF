package com.kgurgul.openksef.domain.invoice

import kotlin.math.roundToInt

object InvoiceBuilder {

    fun buildXml(data: InvoiceData): String {
        val totalNet = data.items.sumOf { it.netValue }
        val totalVat = data.items.sumOf { it.grossValue - it.netValue }
        val totalGross = data.items.sumOf { it.grossValue }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<Faktura xmlns="http://crd.gov.pl/wzor/2023/06/29/12648/">""")
            appendLine("""  <Naglowek>""")
            appendLine("""    <KodFormularza kodSystemowy="FA (2)" wersjaSchemy="1-0E">FA</KodFormularza>""")
            appendLine("""    <WariantFormularza>2</WariantFormularza>""")
            appendLine("""    <DataWytworzeniaFa>${data.issueDate}T00:00:00</DataWytworzeniaFa>""")
            appendLine("""    <SystemInfo>Open KSeF</SystemInfo>""")
            appendLine("""  </Naglowek>""")
            appendLine("""  <Podmiot1>""")
            appendLine("""    <DaneIdentyfikacyjne>""")
            appendLine("""      <NIP>${data.sellerNip}</NIP>""")
            appendLine("""      <Nazwa>${escapeXml(data.sellerName)}</Nazwa>""")
            appendLine("""    </DaneIdentyfikacyjne>""")
            if (data.sellerAddress.isNotBlank()) {
                appendLine("""    <Adres>""")
                appendLine("""      <KodKraju>PL</KodKraju>""")
                appendLine("""      <AdresL1>${escapeXml(data.sellerAddress)}</AdresL1>""")
                appendLine("""    </Adres>""")
            }
            appendLine("""  </Podmiot1>""")
            appendLine("""  <Podmiot2>""")
            appendLine("""    <DaneIdentyfikacyjne>""")
            appendLine("""      <NIP>${data.buyerNip}</NIP>""")
            appendLine("""      <Nazwa>${escapeXml(data.buyerName)}</Nazwa>""")
            appendLine("""    </DaneIdentyfikacyjne>""")
            if (data.buyerAddress.isNotBlank()) {
                appendLine("""    <Adres>""")
                appendLine("""      <KodKraju>PL</KodKraju>""")
                appendLine("""      <AdresL1>${escapeXml(data.buyerAddress)}</AdresL1>""")
                appendLine("""    </Adres>""")
            }
            appendLine("""  </Podmiot2>""")
            appendLine("""  <Fa>""")
            appendLine("""    <KodWaluty>${data.currency}</KodWaluty>""")
            appendLine("""    <P_1>${data.issueDate}</P_1>""")
            appendLine("""    <P_2>${escapeXml(data.invoiceNumber)}</P_2>""")
            appendLine("""    <P_13_1>${formatAmount(totalNet)}</P_13_1>""")
            appendLine("""    <P_14_1>${formatAmount(totalVat)}</P_14_1>""")
            appendLine("""    <P_15>${formatAmount(totalGross)}</P_15>""")
            appendLine("""    <Adnotacje>""")
            appendLine("""      <P_16>2</P_16>""")
            appendLine("""      <P_17>2</P_17>""")
            appendLine("""      <P_18>2</P_18>""")
            appendLine("""      <P_18A>2</P_18A>""")
            appendLine("""    </Adnotacje>""")

            data.items.forEachIndexed { index, item ->
                appendLine("""    <FaWiersz>""")
                appendLine("""      <NrWierszaFa>${index + 1}</NrWierszaFa>""")
                appendLine("""      <P_7>${escapeXml(item.description)}</P_7>""")
                appendLine("""      <P_8A>szt.</P_8A>""")
                appendLine("""      <P_8B>${formatAmount(item.quantity)}</P_8B>""")
                appendLine("""      <P_9A>${formatAmount(item.unitPrice)}</P_9A>""")
                appendLine("""      <P_11>${formatAmount(item.netValue)}</P_11>""")
                appendLine("""      <P_12>${item.vatRate}</P_12>""")
                appendLine("""    </FaWiersz>""")
            }

            appendLine("""  </Fa>""")
            appendLine("""</Faktura>""")
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatAmount(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        val str = rounded.toString()
        val dotIndex = str.indexOf('.')
        return if (dotIndex == -1) {
            "$str.00"
        } else {
            val decimals = str.length - dotIndex - 1
            when {
                decimals == 1 -> "${str}0"
                decimals >= 2 -> str.substring(0, dotIndex + 3)
                else -> str
            }
        }
    }
}
