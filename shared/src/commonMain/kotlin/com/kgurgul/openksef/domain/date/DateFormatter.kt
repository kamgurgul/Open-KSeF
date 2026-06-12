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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.char

/**
 * Formats dates for display in the UI using the Polish locale format `dd.MM.yyyy`.
 *
 * The KSeF API returns dates as ISO-8601 strings (e.g. `2024-01-15` or `2024-01-15T10:30:00`). This
 * formatter converts them to the locale-appropriate display form. The format is fixed to Polish
 * because the app deals with Polish e-invoices, so dates must read the same regardless of the
 * device's region settings (mirroring
 * [CurrencyFormatter][com.kgurgul.openksef.domain.money.CurrencyFormatter]).
 */
object DateFormatter {

    private val displayFormat = LocalDate.Format {
        day()
        char('.')
        monthNumber()
        char('.')
        year()
    }

    /**
     * Formats an ISO-8601 date (or date-time) string as `dd.MM.yyyy`. Only the date part is used.
     * Returns the original string unchanged when it cannot be parsed.
     */
    fun format(isoDate: String): String {
        if (isoDate.isBlank()) return isoDate
        val datePart = isoDate.take(10)
        return runCatching { displayFormat.format(LocalDate.parse(datePart)) }.getOrDefault(isoDate)
    }
}
