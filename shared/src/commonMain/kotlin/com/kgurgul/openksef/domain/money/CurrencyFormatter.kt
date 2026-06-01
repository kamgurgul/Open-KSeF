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

package com.kgurgul.openksef.domain.money

/**
 * Formats a monetary amount using the Polish locale (pl-PL).
 *
 * Implementations always render exactly two fraction digits and apply Polish grouping and decimal
 * separators, e.g. `1 234,50`. The locale is fixed because the app issues Polish e-invoices, so
 * amounts must read the same regardless of the device's region settings.
 */
expect object CurrencyFormatter {
    fun format(amount: Double, currencyCode: String, hideCurrencySymbol: Boolean): String
}
