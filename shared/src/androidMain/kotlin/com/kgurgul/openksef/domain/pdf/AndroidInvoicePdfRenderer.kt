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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * Draws an [InvoiceDocument] onto a native Android [PdfDocument] canvas.
 *
 * Apache FOP (used on desktop) cannot run on Android, so the invoice is laid out manually here. The
 * output is a clean, readable A4 invoice rather than a byte-for-byte copy of the official KSeF
 * visualization. Labels are Polish to match the nature of the e-invoice document.
 */
internal class AndroidInvoicePdfRenderer {

    private val pdf = PdfDocument()
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var pageIndex = 0
    private var cursorY = 0f

    private val titlePaint = paint(size = 20f, bold = true, color = ACCENT)
    private val sectionPaint = paint(size = 9f, bold = true, color = Color.WHITE)
    private val labelPaint = paint(size = 8f, bold = false, color = MUTED)
    private val valuePaint = paint(size = 9.5f, bold = false, color = INK)
    private val strongPaint = paint(size = 9.5f, bold = true, color = INK)
    private val tableHeaderPaint = paint(size = 8f, bold = true, color = Color.WHITE)
    private val cellPaint = paint(size = 8.5f, bold = false, color = INK)
    private val totalLabelPaint = paint(size = 9.5f, bold = false, color = INK)
    private val totalValuePaint = paint(size = 9.5f, bold = true, color = INK)
    private val grandTotalPaint = paint(size = 12f, bold = true, color = ACCENT)
    private val footerPaint = paint(size = 7.5f, bold = false, color = MUTED)
    private val rulePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = BORDER
        }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun render(document: InvoiceDocument, ksefReferenceNumber: String): ByteArray {
        startPage()
        drawTitleBlock(document, ksefReferenceNumber)
        drawParties(document)
        drawItemsTable(document)
        drawTotals(document)
        drawPaymentSection(document.payment)
        drawAnnotationsSection(document.annotations)
        drawKeyValueSection("Dodatkowy opis", document.additionalDescriptions)
        drawFooterSection(document.footerLines)
        finishPage()

        return ByteArrayOutputStream().use { out ->
            pdf.writeTo(out)
            pdf.close()
            out.toByteArray()
        }
    }

    // region Page lifecycle

    private fun startPage() {
        pageIndex++
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        page = pdf.startPage(info)
        canvas = page.canvas
        cursorY = MARGIN
    }

    private fun finishPage() {
        drawFooter()
        pdf.finishPage(page)
    }

    private fun newPage() {
        finishPage()
        startPage()
    }

    private fun ensureSpace(needed: Float) {
        if (cursorY + needed > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) newPage()
    }

    // endregion

    // region Sections

    private fun drawTitleBlock(document: InvoiceDocument, ksefReferenceNumber: String) {
        canvas.drawText("FAKTURA", MARGIN, cursorY + 16f, titlePaint)

        val rightX = PAGE_WIDTH - MARGIN
        drawRightLabelValue(
            "Numer faktury",
            document.invoiceNumber.ifBlank { "—" },
            rightX,
            cursorY,
        )
        drawRightLabelValue(
            "Data wystawienia",
            document.issueDate.ifBlank { "—" },
            rightX,
            cursorY + 24f,
        )
        cursorY += 30f

        if (ksefReferenceNumber.isNotBlank()) {
            canvas.drawText("Numer KSeF: $ksefReferenceNumber", MARGIN, cursorY + 10f, labelPaint)
            cursorY += 14f
        }
        cursorY += 6f
        canvas.drawLine(MARGIN, cursorY, PAGE_WIDTH - MARGIN, cursorY, rulePaint)
        cursorY += SECTION_GAP
    }

    private fun drawParties(document: InvoiceDocument) {
        val gap = 16f
        val boxWidth = (CONTENT_WIDTH - gap) / 2f
        val sellerHeight = partyBoxHeight(document.seller, boxWidth)
        val buyerHeight = partyBoxHeight(document.buyer, boxWidth)
        val boxHeight = maxOf(sellerHeight, buyerHeight)

        ensureSpace(boxHeight + SECTION_GAP)
        drawPartyBox("Sprzedawca", document.seller, MARGIN, cursorY, boxWidth, boxHeight)
        drawPartyBox(
            "Nabywca",
            document.buyer,
            MARGIN + boxWidth + gap,
            cursorY,
            boxWidth,
            boxHeight,
        )
        cursorY += boxHeight + SECTION_GAP
    }

    private fun drawItemsTable(document: InvoiceDocument) {
        ensureSpace(ROW_HEIGHT * 2)
        drawTableHeader()
        if (document.items.isEmpty()) {
            canvas.drawText("Brak pozycji", MARGIN + 6f, cursorY + 13f, cellPaint)
            cursorY += ROW_HEIGHT
            return
        }
        document.items.forEach { item ->
            val nameLines = wrap(item.name.ifBlank { "—" }, cellPaint, COLUMNS[1].width - 8f)
            val rowHeight = maxOf(ROW_HEIGHT, nameLines.size * LINE_HEIGHT + 8f)
            if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) {
                newPage()
                drawTableHeader()
            }
            drawTableRow(item, nameLines, rowHeight)
        }
    }

    private fun drawTotals(document: InvoiceDocument) {
        val boxWidth = 230f
        val boxHeight = 76f
        ensureSpace(boxHeight + SECTION_GAP)
        cursorY += SECTION_GAP

        val left = PAGE_WIDTH - MARGIN - boxWidth
        val top = cursorY
        fillPaint.color = SURFACE
        canvas.drawRect(left, top, left + boxWidth, top + boxHeight, fillPaint)
        canvas.drawRect(left, top, left + boxWidth, top + boxHeight, rulePaint)

        val labelX = left + 14f
        val valueX = left + boxWidth - 14f
        drawTotalRow(
            "Razem netto",
            money(document.totalNet, document.currency),
            labelX,
            valueX,
            top + 18f,
        )
        drawTotalRow(
            "Razem VAT",
            money(document.totalVat, document.currency),
            labelX,
            valueX,
            top + 34f,
        )
        canvas.drawLine(labelX, top + 44f, valueX, top + 44f, rulePaint)

        canvas.drawText("Razem brutto", labelX, top + 62f, totalLabelPaint)
        drawTextRight(
            money(document.totalGross, document.currency),
            valueX,
            top + 64f,
            grandTotalPaint,
        )
        cursorY += boxHeight
    }

    private fun drawFooter() {
        val baseline = PAGE_HEIGHT - MARGIN + 16f
        canvas.drawText("Wygenerowano w aplikacji Open KSeF", MARGIN, baseline, footerPaint)
        drawTextRight("Strona $pageIndex", PAGE_WIDTH - MARGIN, baseline, footerPaint)
    }

    // endregion

    // region Extended sections

    private fun drawPaymentSection(payment: InvoicePaymentInfo?) {
        if (payment == null) return
        drawSectionHeader("Płatność")
        if (payment.method.isNotBlank()) drawDetailRow("Forma płatności", payment.method)
        if (payment.dueDate.isNotBlank()) drawDetailRow("Termin płatności", payment.dueDate)
        drawDetailRow("Zapłacono", if (payment.isPaid) "Tak" else "Nie")
        if (payment.paymentDate.isNotBlank()) drawDetailRow("Data zapłaty", payment.paymentDate)
        if (payment.bankName.isNotBlank()) drawDetailRow("Bank", payment.bankName)
        if (payment.bankAccount.isNotBlank()) drawDetailRow("Rachunek bankowy", payment.bankAccount)
    }

    private fun drawAnnotationsSection(annotations: List<String>) {
        if (annotations.isEmpty()) return
        drawSectionHeader("Adnotacje")
        annotations.forEach(::drawBulletLine)
    }

    private fun drawKeyValueSection(title: String, entries: List<InvoiceKeyValue>) {
        if (entries.isEmpty()) return
        drawSectionHeader(title)
        entries.forEach { drawDetailRow(it.key.ifBlank { "—" }, it.value.ifBlank { "—" }) }
    }

    private fun drawFooterSection(lines: List<String>) {
        if (lines.isEmpty()) return
        drawSectionHeader("Stopka faktury")
        lines.forEach(::drawParagraph)
    }

    private fun drawSectionHeader(title: String) {
        cursorY += SECTION_GAP
        ensureSpace(SECTION_HEADER_HEIGHT + ROW_HEIGHT)
        fillPaint.color = ACCENT
        canvas.drawRect(
            MARGIN,
            cursorY,
            MARGIN + CONTENT_WIDTH,
            cursorY + SECTION_HEADER_HEIGHT,
            fillPaint,
        )
        canvas.drawText(
            title,
            MARGIN + BOX_PADDING,
            cursorY + SECTION_HEADER_HEIGHT - 5f,
            sectionPaint,
        )
        cursorY += SECTION_HEADER_HEIGHT
    }

    /** Draws a label/value pair, wrapping the value within the content column. */
    private fun drawDetailRow(label: String, value: String) {
        val valueWidth = CONTENT_WIDTH - DETAIL_LABEL_WIDTH - 2 * BOX_PADDING
        val valueLines = wrap(value, valuePaint, valueWidth, maxLines = 6).ifEmpty { listOf("—") }
        val rowHeight = valueLines.size * LINE_HEIGHT + 6f
        if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) newPage()
        val baseline = cursorY + 11f
        canvas.drawText(label, MARGIN + BOX_PADDING, baseline, labelPaint)
        var y = baseline
        valueLines.forEach { line ->
            canvas.drawText(line, MARGIN + BOX_PADDING + DETAIL_LABEL_WIDTH, y, valuePaint)
            y += LINE_HEIGHT
        }
        cursorY += rowHeight
    }

    /** Draws a single bulleted, wrapped line of text. */
    private fun drawBulletLine(text: String) {
        val textWidth = CONTENT_WIDTH - 2 * BOX_PADDING - BULLET_INDENT
        val lines = wrap(text, valuePaint, textWidth, maxLines = 4).ifEmpty { listOf("—") }
        val rowHeight = lines.size * LINE_HEIGHT + 4f
        if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) newPage()
        var y = cursorY + 11f
        canvas.drawText("•", MARGIN + BOX_PADDING, y, valuePaint)
        lines.forEach { line ->
            canvas.drawText(line, MARGIN + BOX_PADDING + BULLET_INDENT, y, valuePaint)
            y += LINE_HEIGHT
        }
        cursorY += rowHeight
    }

    /** Draws a full-width, wrapped paragraph of text. */
    private fun drawParagraph(text: String) {
        val lines =
            wrap(text, valuePaint, CONTENT_WIDTH - 2 * BOX_PADDING, maxLines = 12).ifEmpty {
                listOf("—")
            }
        val rowHeight = lines.size * LINE_HEIGHT + 4f
        if (cursorY + rowHeight > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) newPage()
        var y = cursorY + 11f
        lines.forEach { line ->
            canvas.drawText(line, MARGIN + BOX_PADDING, y, valuePaint)
            y += LINE_HEIGHT
        }
        cursorY += rowHeight
    }

    // endregion

    // region Parties helpers

    private fun partyBoxHeight(party: InvoiceDocumentParty, width: Float): Float {
        val addressLines = wrap(party.address, valuePaint, width - 2 * BOX_PADDING).size
        val rows = 1 /* name */ + 1 /* tax id */ + addressLines.coerceAtLeast(1)
        return BOX_HEADER_HEIGHT + BOX_PADDING * 2 + rows * LINE_HEIGHT
    }

    private fun drawPartyBox(
        title: String,
        party: InvoiceDocumentParty,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ) {
        fillPaint.color = SURFACE
        canvas.drawRect(x, y, x + width, y + height, fillPaint)
        canvas.drawRect(x, y, x + width, y + height, rulePaint)

        fillPaint.color = ACCENT
        canvas.drawRect(x, y, x + width, y + BOX_HEADER_HEIGHT, fillPaint)
        canvas.drawText(title, x + BOX_PADDING, y + BOX_HEADER_HEIGHT - 5f, sectionPaint)

        var textY = y + BOX_HEADER_HEIGHT + BOX_PADDING + 9f
        val textX = x + BOX_PADDING
        canvas.drawText(party.name.ifBlank { "—" }, textX, textY, strongPaint)
        textY += LINE_HEIGHT
        canvas.drawText("NIP: ${party.taxId.ifBlank { "—" }}", textX, textY, valuePaint)
        textY += LINE_HEIGHT
        wrap(party.address, valuePaint, width - 2 * BOX_PADDING).forEach { line ->
            canvas.drawText(line, textX, textY, valuePaint)
            textY += LINE_HEIGHT
        }
    }

    // endregion

    // region Table helpers

    private fun drawTableHeader() {
        val top = cursorY
        fillPaint.color = ACCENT
        canvas.drawRect(MARGIN, top, MARGIN + CONTENT_WIDTH, top + TABLE_HEADER_HEIGHT, fillPaint)
        var x = MARGIN
        COLUMNS.forEach { column ->
            drawCell(column.title, x, top + 13f, column, tableHeaderPaint)
            x += column.width
        }
        cursorY = top + TABLE_HEADER_HEIGHT
    }

    private fun drawTableRow(item: InvoiceDocumentLine, nameLines: List<String>, rowHeight: Float) {
        val top = cursorY
        canvas.drawRect(MARGIN, top, MARGIN + CONTENT_WIDTH, top + rowHeight, rulePaint)

        val firstBaseline = top + 13f
        val values =
            listOf(
                item.number.ifBlank { "—" },
                "", // name drawn separately (multi-line)
                item.quantity,
                item.unit,
                item.unitNetPrice,
                item.netValue,
                vatLabel(item.vatRate),
            )
        var x = MARGIN
        COLUMNS.forEachIndexed { index, column ->
            if (index != 1) drawCell(values[index], x, firstBaseline, column, cellPaint)
            x += column.width
        }

        var nameBaseline = firstBaseline
        nameLines.forEach { line ->
            canvas.drawText(line, MARGIN + COLUMNS[0].width + 4f, nameBaseline, cellPaint)
            nameBaseline += LINE_HEIGHT
        }
        cursorY = top + rowHeight
    }

    private fun drawCell(
        text: String,
        columnX: Float,
        baseline: Float,
        column: Column,
        paint: Paint,
    ) {
        when (column.align) {
            Align.LEFT -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(text, columnX + 4f, baseline, paint)
            }
            Align.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, columnX + column.width / 2f, baseline, paint)
            }
            Align.RIGHT -> {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(text, columnX + column.width - 4f, baseline, paint)
            }
        }
        paint.textAlign = Paint.Align.LEFT
    }

    // endregion

    // region Small helpers

    private fun drawTotalRow(
        label: String,
        value: String,
        labelX: Float,
        valueX: Float,
        baseline: Float,
    ) {
        canvas.drawText(label, labelX, baseline, totalLabelPaint)
        drawTextRight(value, valueX, baseline, totalValuePaint)
    }

    private fun drawRightLabelValue(label: String, value: String, rightX: Float, top: Float) {
        drawTextRight(label, rightX, top + 8f, labelPaint)
        drawTextRight(value, rightX, top + 20f, strongPaint)
    }

    private fun drawTextRight(text: String, rightX: Float, baseline: Float, paint: Paint) {
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(text, rightX, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun wrap(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int = MAX_NAME_LINES,
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.trim().split(Regex("\\s+"))
        val lines = ArrayList<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                if (current.isEmpty()) current.append(word) else current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return if (lines.size <= maxLines) {
            lines
        } else {
            lines.take(maxLines).toMutableList().also {
                it[maxLines - 1] = it[maxLines - 1].take(40).trimEnd() + "…"
            }
        }
    }

    private fun money(amount: String, currency: String): String =
        if (amount.isBlank()) "—" else "$amount $currency"

    private fun vatLabel(rate: String): String =
        when {
            rate.isBlank() -> "—"
            rate.all { it.isDigit() } -> "$rate%"
            else -> rate
        }

    private fun paint(size: Float, bold: Boolean, color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    // endregion

    private enum class Align {
        LEFT,
        CENTER,
        RIGHT,
    }

    private class Column(val title: String, val width: Float, val align: Align)

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val SECTION_GAP = 18f
        const val LINE_HEIGHT = 13f
        const val ROW_HEIGHT = 22f
        const val TABLE_HEADER_HEIGHT = 20f
        const val BOX_HEADER_HEIGHT = 18f
        const val BOX_PADDING = 8f
        const val FOOTER_RESERVE = 26f
        const val MAX_NAME_LINES = 4
        const val SECTION_HEADER_HEIGHT = 18f
        const val DETAIL_LABEL_WIDTH = 130f
        const val BULLET_INDENT = 14f

        val INK = 0xFF202124.toInt()
        val MUTED = 0xFF5F6368.toInt()
        val ACCENT = 0xFF1F3B57.toInt()
        val BORDER = 0xFFBDC1C6.toInt()
        val SURFACE = 0xFFF1F3F4.toInt()

        val COLUMNS =
            listOf(
                Column("Lp.", 26f, Align.CENTER),
                Column("Nazwa", 197f, Align.LEFT),
                Column("Ilość", 52f, Align.RIGHT),
                Column("j.m.", 38f, Align.CENTER),
                Column("Cena netto", 68f, Align.RIGHT),
                Column("Wartość netto", 76f, Align.RIGHT),
                Column("VAT", 58f, Align.CENTER),
            )
    }
}
