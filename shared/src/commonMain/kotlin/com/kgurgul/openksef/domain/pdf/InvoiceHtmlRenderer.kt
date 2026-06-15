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

/**
 * Builds a self-contained, styled HTML document from an [InvoiceDocument].
 *
 * The iOS exporter feeds this markup to `UIPrintPageRenderer` to produce a PDF. Keeping the markup
 * in common code makes it unit-testable and keeps the iOS field coverage in step with the Android
 * `PdfDocument` renderer. Labels are Polish to match the nature of the e-invoice document.
 */
object InvoiceHtmlRenderer {

    fun render(document: InvoiceDocument, ksefReferenceNumber: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<style>").append(CSS).append("</style></head><body>")
        appendHeader(document, ksefReferenceNumber)
        appendParties(document)
        appendItems(document)
        appendTotals(document)
        appendPayment(document.payment)
        appendBulletSection("Adnotacje", document.annotations)
        appendKeyValueSection("Dodatkowy opis", document.additionalDescriptions)
        appendFooter(document.footerLines)
        append("</body></html>")
    }

    private fun StringBuilder.appendHeader(document: InvoiceDocument, ksefReferenceNumber: String) {
        append("<table class=\"head\"><tr><td>")
        append("<h1>FAKTURA</h1>")
        if (ksefReferenceNumber.isNotBlank()) {
            append("<div class=\"muted\">Numer KSeF: ")
            append(esc(ksefReferenceNumber))
            append("</div>")
        }
        append("</td><td class=\"right\">")
        appendField("Numer faktury", document.invoiceNumber.ifBlank { "—" })
        appendField("Data wystawienia", document.issueDate.ifBlank { "—" })
        append("</td></tr></table><div class=\"rule\"></div>")
    }

    private fun StringBuilder.appendField(label: String, value: String) {
        append("<div class=\"muted\">").append(esc(label)).append("</div>")
        append("<div class=\"strong\">").append(esc(value)).append("</div>")
    }

    private fun StringBuilder.appendParties(document: InvoiceDocument) {
        append("<table class=\"parties\"><tr><td>")
        appendPartyBox("Sprzedawca", document.seller)
        append("</td><td>")
        appendPartyBox("Nabywca", document.buyer)
        append("</td></tr></table>")
    }

    private fun StringBuilder.appendPartyBox(title: String, party: InvoiceDocumentParty) {
        append("<div class=\"party\"><div class=\"box-title\">")
        append(esc(title))
        append("</div><div class=\"box-body\">")
        append("<div class=\"strong\">").append(esc(party.name.ifBlank { "—" })).append("</div>")
        append("<div>NIP: ").append(esc(party.taxId.ifBlank { "—" })).append("</div>")
        if (party.address.isNotBlank()) {
            append("<div>").append(esc(party.address)).append("</div>")
        }
        append("</div></div>")
    }

    private fun StringBuilder.appendItems(document: InvoiceDocument) {
        append("<table class=\"items\"><thead><tr>")
        for (header in ITEM_HEADERS) append("<th>").append(esc(header)).append("</th>")
        append("</tr></thead><tbody>")
        if (document.items.isEmpty()) {
            append("<tr><td colspan=\"7\">Brak pozycji</td></tr>")
        } else {
            for (item in document.items) {
                append("<tr>")
                cell(item.number.ifBlank { "—" }, "center")
                cell(item.name.ifBlank { "—" }, null)
                cell(item.quantity, "num")
                cell(item.unit, "center")
                cell(item.unitNetPrice, "num")
                cell(item.netValue, "num")
                cell(vatLabel(item.vatRate), "center")
                append("</tr>")
            }
        }
        append("</tbody></table>")
    }

    private fun StringBuilder.cell(text: String, cssClass: String?) {
        if (cssClass == null) append("<td>")
        else append("<td class=\"").append(cssClass).append("\">")
        append(esc(text)).append("</td>")
    }

    private fun StringBuilder.appendTotals(document: InvoiceDocument) {
        append("<table class=\"totals\">")
        totalRow("Razem netto", money(document.totalNet, document.currency), grand = false)
        totalRow("Razem VAT", money(document.totalVat, document.currency), grand = false)
        totalRow("Razem brutto", money(document.totalGross, document.currency), grand = true)
        append("</table>")
    }

    private fun StringBuilder.totalRow(label: String, value: String, grand: Boolean) {
        append(if (grand) "<tr class=\"grand\">" else "<tr>")
        append("<td>").append(esc(label)).append("</td>")
        append("<td class=\"num\">").append(esc(value)).append("</td></tr>")
    }

    private fun StringBuilder.appendPayment(payment: InvoicePaymentInfo?) {
        if (payment == null) return
        val rows = buildList {
            if (payment.method.isNotBlank()) add(InvoiceKeyValue("Forma płatności", payment.method))
            if (payment.dueDate.isNotBlank()) {
                add(InvoiceKeyValue("Termin płatności", payment.dueDate))
            }
            add(InvoiceKeyValue("Zapłacono", if (payment.isPaid) "Tak" else "Nie"))
            if (payment.paymentDate.isNotBlank()) {
                add(InvoiceKeyValue("Data zapłaty", payment.paymentDate))
            }
            if (payment.bankName.isNotBlank()) add(InvoiceKeyValue("Bank", payment.bankName))
            if (payment.bankAccount.isNotBlank()) {
                add(InvoiceKeyValue("Rachunek bankowy", payment.bankAccount))
            }
        }
        appendKeyValueSection("Płatność", rows)
    }

    private fun StringBuilder.appendBulletSection(title: String, items: List<String>) {
        if (items.isEmpty()) return
        appendSectionStart(title)
        append("<ul>")
        for (item in items) append("<li>").append(esc(item)).append("</li>")
        append("</ul></div>")
    }

    private fun StringBuilder.appendKeyValueSection(title: String, entries: List<InvoiceKeyValue>) {
        if (entries.isEmpty()) return
        appendSectionStart(title)
        for (entry in entries) {
            append("<div class=\"kv\"><span class=\"k\">")
            append(esc(entry.key.ifBlank { "—" }))
            append("</span><span>")
            append(esc(entry.value.ifBlank { "—" }))
            append("</span></div>")
        }
        append("</div>")
    }

    private fun StringBuilder.appendFooter(lines: List<String>) {
        if (lines.isEmpty()) return
        appendSectionStart("Stopka faktury")
        for (line in lines) append("<div class=\"footer-line\">").append(esc(line)).append("</div>")
        append("</div>")
    }

    private fun StringBuilder.appendSectionStart(title: String) {
        append("<div class=\"section\"><div class=\"section-title\">")
        append(esc(title))
        append("</div>")
    }

    private fun vatLabel(rate: String): String =
        when {
            rate.isBlank() -> "—"
            rate.all { it.isDigit() } -> "$rate%"
            else -> rate
        }

    private fun money(amount: String, currency: String): String =
        if (amount.isBlank()) "—" else "$amount $currency"

    /** Escapes the five characters that are unsafe in HTML text content. */
    private fun esc(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    private val ITEM_HEADERS =
        listOf("Lp.", "Nazwa", "Ilość", "j.m.", "Cena netto", "Wartość netto", "VAT")

    private val CSS =
        """
        @page { margin: 0; }
        body { font-family: -apple-system, 'Helvetica Neue', Helvetica, sans-serif;
               color: #202124; font-size: 10px; margin: 0;
               box-sizing: border-box; padding: 40px; }
        h1 { font-size: 21px; color: #1F3B57; margin: 0 0 2px 0; }
        .muted { color: #5F6368; font-size: 8px; }
        .strong { font-weight: bold; }
        .right { text-align: right; }
        .rule { border-bottom: 1px solid #BDC1C6; margin: 6px 0 12px 0; }
        table.head { width: 100%; }
        table.head td { vertical-align: top; }
        table.parties { width: 100%; border-collapse: separate; border-spacing: 10px 0; }
        table.parties td { width: 50%; vertical-align: top; }
        .party { border: 1px solid #BDC1C6; }
        .box-title { background: #1F3B57; color: #ffffff; font-weight: bold;
                     font-size: 9px; padding: 4px 8px; }
        .box-body { padding: 8px; }
        .box-body div { margin: 1px 0; }
        table.items { width: 100%; border-collapse: collapse; margin-top: 14px; }
        table.items th { background: #1F3B57; color: #ffffff; font-size: 8px;
                         padding: 5px 4px; text-align: left; }
        table.items td { border: 1px solid #BDC1C6; padding: 4px; font-size: 8px;
                         vertical-align: top; }
        .num { text-align: right; }
        .center { text-align: center; }
        table.totals { margin-top: 12px; margin-left: auto; width: 230px;
                       border: 1px solid #BDC1C6; border-collapse: collapse; }
        table.totals td { padding: 3px 10px; }
        table.totals tr.grand td { font-size: 12px; font-weight: bold; color: #1F3B57;
                                   border-top: 1px solid #BDC1C6; }
        .section { margin-top: 16px; page-break-inside: avoid; }
        .section-title { background: #1F3B57; color: #ffffff; font-weight: bold;
                         font-size: 9px; padding: 4px 8px; margin-bottom: 6px; }
        .kv { margin: 3px 0; }
        .kv .k { color: #5F6368; display: inline-block; width: 150px; vertical-align: top; }
        ul { margin: 4px 0; padding-left: 18px; }
        li { margin: 2px 0; }
        .footer-line { margin: 3px 0; }
        """
            .trimIndent()
}
