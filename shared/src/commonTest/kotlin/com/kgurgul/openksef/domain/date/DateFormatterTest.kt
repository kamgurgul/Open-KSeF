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

package com.kgurgul.openksef.domain.date

import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormatterTest {

    @Test
    fun format_isoDate_returnsPolishFormat() {
        assertEquals("15.01.2024", DateFormatter.format("2024-01-15"))
    }

    @Test
    fun format_isoDateTime_usesOnlyDatePart() {
        assertEquals("15.01.2024", DateFormatter.format("2024-01-15T10:30:00"))
    }

    @Test
    fun format_padsSingleDigitDayAndMonth() {
        assertEquals("05.09.2024", DateFormatter.format("2024-09-05"))
    }

    @Test
    fun format_blankString_returnsUnchanged() {
        assertEquals("", DateFormatter.format(""))
    }

    @Test
    fun format_unparseableString_returnsUnchanged() {
        assertEquals("not a date", DateFormatter.format("not a date"))
    }
}
