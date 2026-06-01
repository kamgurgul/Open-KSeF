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

import com.kgurgul.openksef.domain.money.Money

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
        val netFromLines = Money.sum(items.mapNotNull { parseAmount(it.netValue) })
        val gross = parseAmount(fa?.childText("P_15"))

        val totalNet = netFromTotals ?: netFromLines
        val totalVat = vatFromTotals ?: if (gross != null) gross - totalNet else Money.ZERO
        val totalGross = gross ?: (totalNet + totalVat)

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
            totalNet = totalNet.toPlainString(),
            totalVat = totalVat.toPlainString(),
            totalGross = totalGross.toPlainString(),
            payment = parsePayment(fa?.child("Platnosc")),
            annotations = parseAnnotations(fa?.child("Adnotacje")),
            additionalDescriptions =
                fa?.children("DodatkowyOpis")?.mapNotNull(::parseKeyValue).orEmpty(),
            footerLines = parseFooter(root.child("Stopka")),
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
    private fun sumRateFields(fa: XmlNode?, prefix: String): Money? {
        if (fa == null) return null
        var total = Money.ZERO
        var found = false
        for (rate in 1..7) {
            val amount = parseAmount(fa.childText("$prefix$rate")) ?: continue
            total += amount
            found = true
        }
        return if (found) total else null
    }

    /** Parses a `DodatkowyOpis` key/value entry, or null when it carries no text. */
    private fun parseKeyValue(node: XmlNode): InvoiceKeyValue? {
        val rawKey = node.childText("Klucz").orEmpty()
        val value = node.childText("Wartosc").orEmpty()
        if (rawKey.isEmpty() && value.isEmpty()) return null
        val lineRef = node.childText("NrWiersza")
        val key =
            when {
                lineRef == null -> rawKey
                rawKey.isEmpty() -> "Poz. $lineRef"
                else -> "Poz. $lineRef · $rawKey"
            }
        return InvoiceKeyValue(key = key, value = value)
    }

    /** Parses the `Platnosc` element, or null when it carries nothing worth showing. */
    private fun parsePayment(node: XmlNode?): InvoicePaymentInfo? {
        if (node == null) return null
        val isPaid = node.childText("Zaplacono") == "1"
        val paymentDate = node.childText("DataZaplaty").orEmpty()
        val dueDate = node.child("TerminPlatnosci")?.childText("Termin").orEmpty()
        val method = paymentMethodLabel(node.childText("FormaPlatnosci"))
        val account = node.child("RachunekBankowy")
        val bankAccount = account?.childText("NrRB").orEmpty()
        val bankName = account?.childText("NazwaBanku").orEmpty()
        val hasContent =
            isPaid ||
                paymentDate.isNotEmpty() ||
                dueDate.isNotEmpty() ||
                method.isNotEmpty() ||
                bankAccount.isNotEmpty() ||
                bankName.isNotEmpty()
        return if (hasContent) {
            InvoicePaymentInfo(
                isPaid = isPaid,
                paymentDate = paymentDate,
                dueDate = dueDate,
                method = method,
                bankAccount = bankAccount,
                bankName = bankName,
            )
        } else {
            null
        }
    }

    /** Maps a `FormaPlatnosci` code to a human-readable Polish label. */
    private fun paymentMethodLabel(code: String?): String =
        when (code) {
            null -> ""
            "1" -> "Gotówka"
            "2" -> "Karta"
            "3" -> "Bon"
            "4" -> "Czek"
            "5" -> "Kredyt"
            "6" -> "Przelew"
            "7" -> "Płatność mobilna"
            else -> code
        }

    /** Collects the active boolean flags of the `Adnotacje` element as readable labels. */
    private fun parseAnnotations(node: XmlNode?): List<String> {
        if (node == null) return emptyList()
        val labels = mutableListOf<String>()
        fun flag(child: String, label: String) {
            if (node.childText(child) == "1") labels += label
        }
        flag("P_16", "Metoda kasowa")
        flag("P_17", "Samofakturowanie")
        flag("P_18", "Odwrotne obciążenie")
        flag("P_18A", "Mechanizm podzielonej płatności (MPP)")
        if (node.child("Zwolnienie")?.childText("P_19") == "1") {
            labels += "Zwolnienie z VAT"
        }
        if (node.child("NoweSrodkiTransportu")?.childText("P_22") == "1") {
            labels += "Nowe środki transportu"
        }
        flag("P_23", "Procedura uproszczona (trójstronna)")
        val pMarzy = node.child("PMarzy")
        if (pMarzy != null && pMarzy.childText("P_PMarzyN") != "1") {
            labels += "Procedura marży"
        }
        return labels
    }

    /** Extracts free-text lines and registry data from the invoice `Stopka` element. */
    private fun parseFooter(node: XmlNode?): List<String> {
        if (node == null) return emptyList()
        val lines = mutableListOf<String>()
        node.children("Informacje").forEach { info ->
            info.childText("StopkaFaktury")?.let { lines += it }
        }
        node.children("Rejestry").forEach { registry ->
            val parts =
                listOfNotNull(
                    registry.childText("PelnaNazwa"),
                    registry.childText("KRS")?.let { "KRS: $it" },
                    registry.childText("REGON")?.let { "REGON: $it" },
                    registry.childText("BDO")?.let { "BDO: $it" },
                )
            if (parts.isNotEmpty()) lines += parts.joinToString("  ·  ")
        }
        return lines
    }
}

/**
 * Parses a decimal amount (e.g. `"1234.56"`) into [Money], or null when the text is missing or not
 * a number.
 */
internal fun parseAmount(value: String?): Money? {
    val raw = value?.trim() ?: return null
    if (raw.isEmpty()) return null
    if (raw.replace(',', '.').toDoubleOrNull() == null) return null
    return Money.fromFormattedString(raw)
}
