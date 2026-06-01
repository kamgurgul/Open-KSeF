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

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

actual object CurrencyFormatter {
    actual fun format(amount: Double, currencyCode: String, hideCurrencySymbol: Boolean): String {
        val locale = Locale.forLanguageTag("pl-PL")
        val format =
            if (hideCurrencySymbol) {
                NumberFormat.getNumberInstance(locale).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
            } else {
                NumberFormat.getCurrencyInstance(locale).apply {
                    runCatching { currency = Currency.getInstance(currencyCode) }
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
            }
        return format.format(amount)
    }
}
