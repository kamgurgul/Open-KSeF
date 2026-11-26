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
import kotlin.test.assertTrue

class EuVatTest {

    @Test
    fun countryCodes_excludePolandAndUseElForGreece() {
        assertFalse("PL" in EuVat.COUNTRY_CODES)
        assertTrue("EL" in EuVat.COUNTRY_CODES)
        assertFalse("GR" in EuVat.COUNTRY_CODES)
        assertEquals(27, EuVat.COUNTRY_CODES.size)
    }

    @Test
    fun normalizeNumber_stripsCountryPrefixAndSeparators() {
        assertEquals("6388047V", EuVat.normalizeNumber("IE", "ie 6388047v"))
        assertEquals("6388047V", EuVat.normalizeNumber("IE", "6388-047V"))
        assertEquals("U12345678", EuVat.normalizeNumber("AT", "ATU 123 456 78"))
    }

    @Test
    fun isValidNumber_followsSchemaPattern() {
        assertTrue(EuVat.isValidNumber("IE", "IE6388047V"))
        assertTrue(EuVat.isValidNumber("DK", "1234567+"))
        assertFalse(EuVat.isValidNumber("IE", ""))
        assertFalse(EuVat.isValidNumber("IE", "IE"))
        assertFalse(EuVat.isValidNumber("DE", "1234567890123"))
    }

    @Test
    fun addressCountryCode_mapsGreeceToIso() {
        assertEquals("GR", EuVat.addressCountryCode("EL"))
        assertEquals("IE", EuVat.addressCountryCode("IE"))
    }
}
