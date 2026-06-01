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

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle

actual object CurrencyFormatter {
    actual fun format(amount: Double, currencyCode: String, hideCurrencySymbol: Boolean): String {
        val formatter =
            NSNumberFormatter().apply {
                locale = NSLocale(localeIdentifier = "pl_PL")
                minimumFractionDigits = 2u
                maximumFractionDigits = 2u
                if (hideCurrencySymbol) {
                    numberStyle = NSNumberFormatterDecimalStyle
                } else {
                    numberStyle = NSNumberFormatterCurrencyStyle
                    this.currencyCode = currencyCode
                }
            }
        return formatter.stringFromNumber(NSNumber(double = amount))
            ?: amount.toString()
    }
}
