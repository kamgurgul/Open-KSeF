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

package com.kgurgul.openksef.data.local.db

import com.kgurgul.openksef.domain.invoice.InvoiceTemplateItem
import com.kgurgul.openksef.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvoiceTemplateItemConverterTest {

    private val converter = InvoiceTemplateItemConverter()

    @Test
    fun toItems_readsRowsWrittenBeforeRateCodes() {
        val legacy =
            """[{"description":"Usługa","quantity":"1","unit":"szt.","unitPrice":"100","vatRate":8},""" +
                    """{"description":"Zero","quantity":"2","unit":"kg","unitPrice":"5","vatRate":0}]"""

        val items = converter.toItems(legacy)

        assertEquals(VatRate.RATE_8, items[0].vatRate)
        assertEquals(VatRate.ZERO_DOMESTIC, items[1].vatRate)
        assertEquals("kg", items[1].unit)
    }

    @Test
    fun fromItems_storesRateCodeAndRoundTrips() {
        val items =
            listOf(
                InvoiceTemplateItem(
                    description = "Google AdMob",
                    quantity = "1",
                    unit = "szt.",
                    unitPrice = "307.10",
                    vatRate = VatRate.NOT_SUBJECT_EU_SERVICES,
                )
            )

        val raw = converter.fromItems(items)

        assertTrue(raw.contains("\"vatRate\":\"np II\""), raw)
        assertEquals(items, converter.toItems(raw))
    }
}
