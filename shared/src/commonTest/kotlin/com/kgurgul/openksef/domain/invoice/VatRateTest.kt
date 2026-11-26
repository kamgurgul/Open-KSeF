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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class VatRateTest {

    @Test
    fun fromCode_resolvesEveryFa3Code() {
        VatRate.entries.forEach { rate -> assertEquals(rate, VatRate.fromCode(rate.code)) }
        assertEquals(VatRate.NOT_SUBJECT_EU_SERVICES, VatRate.fromCode(" np II "))
    }

    @Test
    fun fromCode_mapsLegacyZeroToDomesticZeroRate() {
        assertEquals(VatRate.ZERO_DOMESTIC, VatRate.fromCode("0"))
    }

    @Test
    fun fromCode_unknownCode_returnsNull() {
        assertNull(VatRate.fromCode("7"))
        assertNull(VatRate.fromCode(""))
    }

    @Test
    fun requiresReverseCharge_onlyForRatesSettledByTheBuyer() {
        assertTrue(VatRate.NOT_SUBJECT_EU_SERVICES.requiresReverseCharge)
        assertTrue(VatRate.DOMESTIC_REVERSE_CHARGE.requiresReverseCharge)
        assertFalse(VatRate.NOT_SUBJECT_ABROAD.requiresReverseCharge)
        assertFalse(VatRate.RATE_23.requiresReverseCharge)
        assertFalse(VatRate.ZERO_INTRA_EU.requiresReverseCharge)
    }

    @Test
    fun serialization_roundTripsAsCode() {
        val json = Json.encodeToString(VatRate.serializer(), VatRate.NOT_SUBJECT_EU_SERVICES)

        assertEquals("\"np II\"", json)
        assertEquals(
            VatRate.NOT_SUBJECT_EU_SERVICES,
            Json.decodeFromString(VatRate.serializer(), json),
        )
    }

    @Test
    fun deserialization_acceptsLegacyNumbersAndFallsBackOnUnknownValues() {
        assertEquals(VatRate.RATE_8, Json.decodeFromString(VatRate.serializer(), "8"))
        assertEquals(VatRate.ZERO_DOMESTIC, Json.decodeFromString(VatRate.serializer(), "0"))
        assertEquals(VatRate.DEFAULT, Json.decodeFromString(VatRate.serializer(), "\"bogus\""))
    }
}
