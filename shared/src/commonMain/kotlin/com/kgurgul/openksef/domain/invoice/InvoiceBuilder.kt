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
import kotlin.math.round

object InvoiceBuilder {

    fun buildXml(data: InvoiceData): String {
        val totalNet = Money.sum(data.items.map { it.netValue })
        val totalVat = Money.sum(data.items.map { it.grossValue - it.netValue })
        val totalGross = Money.sum(data.items.map { it.grossValue })

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<Faktura xmlns="http://crd.gov.pl/wzor/2023/06/29/12648/">""")
            appendLine("""  <Naglowek>""")
            appendLine(
                """    <KodFormularza kodSystemowy="FA (2)" wersjaSchemy="1-0E">FA</KodFormularza>"""
            )
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
            appendLine("""    <P_13_1>${totalNet.toPlainString()}</P_13_1>""")
            appendLine("""    <P_14_1>${totalVat.toPlainString()}</P_14_1>""")
            appendLine("""    <P_15>${totalGross.toPlainString()}</P_15>""")
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
                appendLine("""      <P_8A>${escapeXml(item.unit)}</P_8A>""")
                appendLine("""      <P_8B>${formatQuantity(item.quantity)}</P_8B>""")
                appendLine("""      <P_9A>${item.unitPrice.toPlainString()}</P_9A>""")
                appendLine("""      <P_11>${item.netValue.toPlainString()}</P_11>""")
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

    /** Renders a (non-monetary) quantity with two fraction digits and a dot separator. */
    private fun formatQuantity(value: Double): String {
        val negative = value < 0
        val hundredths = round(value * 100).toLong()
        val abs = if (negative) -hundredths else hundredths
        val fraction = (abs % 100).toString().padStart(2, '0')
        return "${if (negative) "-" else ""}${abs / 100}.$fraction"
    }
}
