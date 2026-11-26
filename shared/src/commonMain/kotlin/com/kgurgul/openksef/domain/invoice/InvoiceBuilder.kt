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

    /**
     * Tax summary fields in FA(3) schema order. Each rate's net amount is summed into its own
     * `P_13_x`, and its tax into `P_14_x` when the rate carries tax.
     */
    private val TAX_SUMMARY_FIELDS =
        listOf(
            TaxSummaryField(VatRate.RATE_23, netField = "P_13_1", taxField = "P_14_1"),
            TaxSummaryField(VatRate.RATE_8, netField = "P_13_2", taxField = "P_14_2"),
            TaxSummaryField(VatRate.RATE_5, netField = "P_13_3", taxField = "P_14_3"),
            TaxSummaryField(VatRate.ZERO_DOMESTIC, netField = "P_13_6_1"),
            TaxSummaryField(VatRate.ZERO_INTRA_EU, netField = "P_13_6_2"),
            TaxSummaryField(VatRate.ZERO_EXPORT, netField = "P_13_6_3"),
            TaxSummaryField(VatRate.NOT_SUBJECT_ABROAD, netField = "P_13_8"),
            TaxSummaryField(VatRate.NOT_SUBJECT_EU_SERVICES, netField = "P_13_9"),
            TaxSummaryField(VatRate.DOMESTIC_REVERSE_CHARGE, netField = "P_13_10"),
        )

    fun buildXml(data: InvoiceData): String {
        val totalGross = Money.sum(data.items.map { it.grossValue })
        val reverseCharge =
            data.reverseCharge || data.items.any { it.vatRate.requiresReverseCharge }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">""")
            appendLine("""  <Naglowek>""")
            appendLine(
                """    <KodFormularza kodSystemowy="FA (3)" wersjaSchemy="1-0E">FA</KodFormularza>"""
            )
            appendLine("""    <WariantFormularza>3</WariantFormularza>""")
            appendLine("""    <DataWytworzeniaFa>${data.issueDate}T00:00:00</DataWytworzeniaFa>""")
            appendLine("""    <SystemInfo>Open KSeF</SystemInfo>""")
            appendLine("""  </Naglowek>""")
            appendLine("""  <Podmiot1>""")
            appendLine("""    <DaneIdentyfikacyjne>""")
            appendLine("""      <NIP>${data.sellerNip}</NIP>""")
            appendLine("""      <Nazwa>${escapeXml(data.sellerName)}</Nazwa>""")
            appendLine("""    </DaneIdentyfikacyjne>""")
            // FA(3) requires the seller address.
            appendLine("""    <Adres>""")
            appendLine("""      <KodKraju>PL</KodKraju>""")
            appendLine("""      <AdresL1>${escapeXml(data.sellerAddress)}</AdresL1>""")
            appendLine("""    </Adres>""")
            appendLine("""  </Podmiot1>""")
            appendLine("""  <Podmiot2>""")
            appendLine("""    <DaneIdentyfikacyjne>""")
            when (val taxId = data.buyerTaxId) {
                is BuyerTaxId.Nip -> appendLine("""      <NIP>${escapeXml(taxId.nip)}</NIP>""")
                is BuyerTaxId.EuVat -> {
                    appendLine("""      <KodUE>${escapeXml(taxId.countryCode)}</KodUE>""")
                    appendLine("""      <NrVatUE>${escapeXml(taxId.number)}</NrVatUE>""")
                }
            }
            appendLine("""      <Nazwa>${escapeXml(data.buyerName)}</Nazwa>""")
            appendLine("""    </DaneIdentyfikacyjne>""")
            if (data.buyerAddress.isNotBlank()) {
                appendLine("""    <Adres>""")
                appendLine("""      <KodKraju>${buyerCountryCode(data.buyerTaxId)}</KodKraju>""")
                appendLine("""      <AdresL1>${escapeXml(data.buyerAddress)}</AdresL1>""")
                appendLine("""    </Adres>""")
            }
            // FA(3) markers: 2 = the buyer is neither a subordinate JST unit nor a VAT group
            // member.
            appendLine("""    <JST>2</JST>""")
            appendLine("""    <GV>2</GV>""")
            appendLine("""  </Podmiot2>""")
            appendLine("""  <Fa>""")
            appendLine("""    <KodWaluty>${data.currency}</KodWaluty>""")
            appendLine("""    <P_1>${data.issueDate}</P_1>""")
            appendLine("""    <P_2>${escapeXml(data.invoiceNumber)}</P_2>""")
            appendTaxSummary(data.items)
            appendLine("""    <P_15>${totalGross.toPlainString()}</P_15>""")
            // Adnotacje requires the full set of markers (1 = "yes", 2 = "no", 1 in the *N
            // variants = "does not apply"). Only reverse charge (P_18) is ever switched on.
            appendLine("""    <Adnotacje>""")
            appendLine("""      <P_16>2</P_16>""")
            appendLine("""      <P_17>2</P_17>""")
            appendLine("""      <P_18>${if (reverseCharge) 1 else 2}</P_18>""")
            appendLine("""      <P_18A>2</P_18A>""")
            appendLine("""      <Zwolnienie>""")
            appendLine("""        <P_19N>1</P_19N>""")
            appendLine("""      </Zwolnienie>""")
            appendLine("""      <NoweSrodkiTransportu>""")
            appendLine("""        <P_22N>1</P_22N>""")
            appendLine("""      </NoweSrodkiTransportu>""")
            appendLine("""      <P_23>2</P_23>""")
            appendLine("""      <PMarzy>""")
            appendLine("""        <P_PMarzyN>1</P_PMarzyN>""")
            appendLine("""      </PMarzy>""")
            appendLine("""    </Adnotacje>""")
            appendLine("""    <RodzajFaktury>VAT</RodzajFaktury>""")

            data.items.forEachIndexed { index, item ->
                appendLine("""    <FaWiersz>""")
                appendLine("""      <NrWierszaFa>${index + 1}</NrWierszaFa>""")
                appendLine("""      <P_7>${escapeXml(item.description)}</P_7>""")
                appendLine("""      <P_8A>${escapeXml(item.unit)}</P_8A>""")
                appendLine("""      <P_8B>${formatQuantity(item.quantity)}</P_8B>""")
                appendLine("""      <P_9A>${item.unitPrice.toPlainString()}</P_9A>""")
                appendLine("""      <P_11>${item.netValue.toPlainString()}</P_11>""")
                appendLine("""      <P_12>${item.vatRate.code}</P_12>""")
                appendLine("""    </FaWiersz>""")
            }

            appendPayment(data.payment)
            appendLine("""  </Fa>""")
            appendLine("""</Faktura>""")
        }
    }

    /** Writes the `P_13_x`/`P_14_x` totals of every rate used on the invoice. */
    private fun StringBuilder.appendTaxSummary(items: List<InvoiceLineItem>) {
        for (field in TAX_SUMMARY_FIELDS) {
            val lines = items.filter { it.vatRate == field.rate }
            if (lines.isEmpty()) continue
            val net = Money.sum(lines.map { it.netValue })
            appendLine("""    <${field.netField}>${net.toPlainString()}</${field.netField}>""")
            if (field.taxField != null) {
                val tax = Money.sum(lines.map { it.grossValue - it.netValue })
                appendLine("""    <${field.taxField}>${tax.toPlainString()}</${field.taxField}>""")
            }
        }
    }

    /**
     * Writes `Platnosc`. Its children must keep the schema order: settlement status, due date,
     * payment form, bank account.
     */
    private fun StringBuilder.appendPayment(payment: InvoicePayment) {
        if (payment.isEmpty) return
        appendLine("""    <Platnosc>""")
        when (val status = payment.status) {
            PaymentStatus.Unpaid -> Unit
            is PaymentStatus.Paid -> {
                appendLine("""      <Zaplacono>1</Zaplacono>""")
                appendLine("""      <DataZaplaty>${escapeXml(status.date)}</DataZaplaty>""")
            }

            is PaymentStatus.PartiallyPaid -> {
                // 1 = partly paid; 2 would mean paid in full over several instalments.
                appendLine("""      <ZnacznikZaplatyCzesciowej>1</ZnacznikZaplatyCzesciowej>""")
                appendLine("""      <ZaplataCzesciowa>""")
                val amount = status.amount.toPlainString()
                appendLine("""        <KwotaZaplatyCzesciowej>$amount</KwotaZaplatyCzesciowej>""")
                val date = escapeXml(status.date)
                appendLine("""        <DataZaplatyCzesciowej>$date</DataZaplatyCzesciowej>""")
                appendLine("""      </ZaplataCzesciowa>""")
            }
        }
        if (payment.dueDate.isNotBlank()) {
            appendLine("""      <TerminPlatnosci>""")
            appendLine("""        <Termin>${escapeXml(payment.dueDate)}</Termin>""")
            appendLine("""      </TerminPlatnosci>""")
        }
        payment.method?.let { appendLine("""      <FormaPlatnosci>${it.code}</FormaPlatnosci>""") }
        payment.bankAccount?.let { account ->
            appendLine("""      <RachunekBankowy>""")
            appendLine("""        <NrRB>${escapeXml(account.number)}</NrRB>""")
            if (account.swift.isNotBlank()) {
                appendLine("""        <SWIFT>${escapeXml(account.swift)}</SWIFT>""")
            }
            if (account.bankName.isNotBlank()) {
                appendLine("""        <NazwaBanku>${escapeXml(account.bankName)}</NazwaBanku>""")
            }
            appendLine("""      </RachunekBankowy>""")
        }
        appendLine("""    </Platnosc>""")
    }

    private fun buyerCountryCode(taxId: BuyerTaxId): String =
        when (taxId) {
            is BuyerTaxId.Nip -> "PL"
            is BuyerTaxId.EuVat -> escapeXml(EuVat.addressCountryCode(taxId.countryCode))
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

private data class TaxSummaryField(
    val rate: VatRate,
    val netField: String,
    val taxField: String? = null,
)
