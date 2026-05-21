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
 * Extracts an [InvoiceDocument] from raw KSeF FA invoice XML.
 *
 * The same core element names (`P_1`, `P_2`, `FaWiersz`, `P_13_x`, `P_15`, …) are shared by the
 * FA(2) and FA(3) schemas, so a single field-based extraction works for both. Missing fields are
 * tolerated and default to empty/zero values.
 */
object InvoiceXmlParser {

    fun parse(xml: String): InvoiceDocument {
        val root = MiniXml.parse(xml)
        val header = root.child("Naglowek")
        val fa = root.child("Fa")

        val items = fa?.children("FaWiersz")?.map(::parseLine).orEmpty()

        val netFromTotals = sumRateFields(fa, prefix = "P_13_")
        val vatFromTotals = sumRateFields(fa, prefix = "P_14_")
        val netFromLines = items.mapNotNull { parseCents(it.netValue) }.sum()
        val grossCents = parseCents(fa?.childText("P_15"))

        val totalNetCents = netFromTotals ?: netFromLines
        val totalVatCents =
            vatFromTotals ?: if (grossCents != null) grossCents - totalNetCents else 0L
        val totalGrossCents = grossCents ?: (totalNetCents + totalVatCents)

        return InvoiceDocument(
            schema = detectSchema(header),
            invoiceNumber = fa?.childText("P_2").orEmpty(),
            issueDate =
                fa?.childText("P_1")
                    ?: header?.childText("DataWytworzeniaFa")?.substringBefore('T').orEmpty(),
            currency = fa?.childText("KodWaluty") ?: "PLN",
            seller = parseParty(root.child("Podmiot1")),
            buyer = parseParty(root.child("Podmiot2")),
            items = items,
            totalNet = formatCents(totalNetCents),
            totalVat = formatCents(totalVatCents),
            totalGross = formatCents(totalGrossCents),
        )
    }

    /** Detects the schema variant from the source XML, defaulting to FA(2) when unknown. */
    fun detectSchema(xml: String): InvoiceSchemaType =
        runCatching { detectSchema(MiniXml.parse(xml).child("Naglowek")) }
            .getOrDefault(InvoiceSchemaType.FA2)

    private fun detectSchema(header: XmlNode?): InvoiceSchemaType {
        val code = header?.child("KodFormularza")?.attributes?.get("kodSystemowy").orEmpty()
        return when {
            code.contains("RR", ignoreCase = true) -> InvoiceSchemaType.FA_RR
            code.contains('3') -> InvoiceSchemaType.FA3
            else -> InvoiceSchemaType.FA2
        }
    }

    private fun parseParty(node: XmlNode?): InvoiceDocumentParty {
        val identity = node?.child("DaneIdentyfikacyjne")
        val name =
            identity?.childText("Nazwa")
                ?: identity?.childText("PelnaNazwa")
                ?: listOfNotNull(
                        identity?.childText("ImiePierwsze"),
                        identity?.childText("Nazwisko"),
                    )
                    .joinToString(" ")
                    .ifBlank { "" }
        val taxId =
            identity?.childText("NIP")
                ?: identity?.childText("NrID")
                ?: identity?.childText("NrVatUE")
                ?: ""
        val address = node?.child("Adres")
        val addressLine =
            listOfNotNull(address?.childText("AdresL1"), address?.childText("AdresL2"))
                .joinToString(", ")
        return InvoiceDocumentParty(name = name, taxId = taxId, address = addressLine)
    }

    private fun parseLine(node: XmlNode): InvoiceDocumentLine =
        InvoiceDocumentLine(
            number = node.childText("NrWierszaFa").orEmpty(),
            name = node.childText("P_7").orEmpty(),
            unit = node.childText("P_8A").orEmpty(),
            quantity = node.childText("P_8B").orEmpty(),
            unitNetPrice = node.childText("P_9A").orEmpty(),
            netValue = node.childText("P_11").orEmpty(),
            vatRate = node.childText("P_12").orEmpty(),
        )

    /** Sums `P_13_1..P_13_7` (or `P_14_*`) rate buckets. Returns null when none are present. */
    private fun sumRateFields(fa: XmlNode?, prefix: String): Long? {
        if (fa == null) return null
        var total = 0L
        var found = false
        for (rate in 1..7) {
            val cents = parseCents(fa.childText("$prefix$rate")) ?: continue
            total += cents
            found = true
        }
        return if (found) total else null
    }
}

/**
 * Parses a decimal amount (e.g. `"1234.56"`) into integer hundredths, or null when not a number.
 */
internal fun parseCents(value: String?): Long? {
    val raw = value?.trim()?.replace(',', '.') ?: return null
    if (raw.isEmpty()) return null
    val negative = raw.startsWith('-')
    val body = raw.trimStart('-', '+')
    val dot = body.indexOf('.')
    return try {
        val whole: Long
        val fraction: Long
        if (dot == -1) {
            whole = body.toLong()
            fraction = 0
        } else {
            whole = body.substring(0, dot).ifEmpty { "0" }.toLong()
            fraction = (body.substring(dot + 1) + "00").substring(0, 2).toLong()
        }
        val total = whole * 100 + fraction
        if (negative) -total else total
    } catch (_: NumberFormatException) {
        null
    }
}

/** Formats integer hundredths back into a `"0.00"`-style decimal string. */
internal fun formatCents(cents: Long): String {
    val negative = cents < 0
    val abs = if (negative) -cents else cents
    val sign = if (negative) "-" else ""
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
