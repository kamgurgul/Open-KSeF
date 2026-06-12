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

import com.kgurgul.openksef.common.Constants.DEFAULT_CURRENCY_CODE
import kotlin.jvm.JvmInline
import kotlin.math.round

/**
 * Represents a monetary value with minor unit precision.
 *
 * The amount is stored as a [Long] of the smallest currency unit (minor units), avoiding
 * floating-point precision issues. For example, 123.99 is stored as 12399 minor units.
 *
 * This class is multiplatform-compatible and provides precise decimal operations without relying on
 * `BigDecimal`.
 *
 * @property minorUnits The value in minor units (smallest currency denomination). For example,
 *   12399 represents 123.99 in the major currency unit.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {

    /** The integer part of the value (major currency units). For 12399 minor units returns 123. */
    val wholePart: Long
        get() = minorUnits / MINOR_UNITS_PER_MAJOR

    /** The fractional part of the value (minor units). For 12399 minor units returns 99. */
    val fractionalPart: Long
        get() = minorUnits % MINOR_UNITS_PER_MAJOR

    /**
     * The value as a [Double]. Use for display/formatting only, never for calculations. For 12399
     * minor units returns 123.99.
     */
    val asDouble: Double
        get() = minorUnits.toDouble() / MINOR_UNITS_PER_MAJOR

    /** True if this value is zero. */
    val isZero: Boolean
        get() = minorUnits == 0L

    /** True if this value is greater than zero. */
    val isPositive: Boolean
        get() = minorUnits > 0L

    /** True if this value is less than zero. */
    val isNegative: Boolean
        get() = minorUnits < 0L

    operator fun plus(other: Money): Money = Money(minorUnits + other.minorUnits)

    operator fun minus(other: Money): Money = Money(minorUnits - other.minorUnits)

    operator fun times(multiplier: Long): Money = Money(minorUnits * multiplier)

    operator fun times(multiplier: Double): Money = Money(round(minorUnits * multiplier).toLong())

    operator fun div(divisor: Long): Money = Money(minorUnits / divisor)

    operator fun unaryMinus(): Money = Money(-minorUnits)

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    /**
     * Returns the gross value obtained by applying the given VAT rate (as a percentage) to this net
     * value. For example, `Money.fromMajorUnits(100).withVatRate(23)` returns 123.00.
     */
    fun withVatRate(vatRatePercent: Int): Money = this * (1.0 + vatRatePercent / 100.0)

    /**
     * Formats the value using the Polish locale with two fraction digits. For 12399 minor units
     * this returns "123,99".
     */
    fun toFormattedString(
        currencyCode: String = DEFAULT_CURRENCY_CODE,
        hideCurrencySymbol: Boolean = true,
    ): String =
        CurrencyFormatter.format(
            amount = asDouble,
            currencyCode = currencyCode,
            hideCurrencySymbol = hideCurrencySymbol,
        )

    /**
     * Converts the value to a locale-independent decimal string with a dot separator, no grouping
     * and exactly two fraction digits. Suitable for XML payloads and text-field editing where
     * locale formatting would interfere with parsing. For 12399 minor units always returns
     * "123.99".
     */
    fun toPlainString(): String {
        val negative = minorUnits < 0
        val abs = if (negative) -minorUnits else minorUnits
        val fraction = (abs % MINOR_UNITS_PER_MAJOR).toString().padStart(2, '0')
        return "${if (negative) "-" else ""}${abs / MINOR_UNITS_PER_MAJOR}.$fraction"
    }

    override fun toString(): String = toPlainString()

    companion object {
        private const val MINOR_UNITS_PER_MAJOR = 100

        /** A zero value. */
        val ZERO = Money(0L)

        /** Creates a [Money] from minor units. `fromMinorUnits(12399)` represents 123.99. */
        fun fromMinorUnits(minorUnits: Long): Money = Money(minorUnits)

        /**
         * Creates a [Money] from major units and optional minor units. `fromMajorUnits(123, 99)`
         * represents 123.99.
         */
        fun fromMajorUnits(majorUnits: Long, minorUnits: Long = 0): Money {
            require(minorUnits in 0..99) { "Minor units must be between 0 and 99" }
            return Money(majorUnits * MINOR_UNITS_PER_MAJOR + minorUnits)
        }

        /**
         * Creates a [Money] from a [Double], rounding to the nearest minor unit. Use with caution
         * due to floating-point precision; prefer [fromMinorUnits] or [fromMajorUnits].
         */
        fun fromDouble(value: Double): Money = Money(round(value * MINOR_UNITS_PER_MAJOR).toLong())

        /**
         * Parses a formatted string into a [Money]. Handles both comma and dot decimal separators
         * and removes thousand separators (spaces, non-breaking spaces, etc.). For example
         * "123,99", "123.99", "2 000,00" or "2,000.99" produce the appropriate value. Returns
         * [ZERO] when the string cannot be parsed.
         */
        fun fromFormattedString(value: String): Money {
            val cleaned = value.trim().replace(" ", "").replace(" ", "").replace(" ", "")

            val lastCommaIndex = cleaned.lastIndexOf(',')
            val lastDotIndex = cleaned.lastIndexOf('.')

            val normalized =
                when {
                    lastCommaIndex > lastDotIndex -> cleaned.replace(".", "").replace(",", ".")
                    lastDotIndex > lastCommaIndex -> cleaned.replace(",", "")
                    lastCommaIndex >= 0 -> cleaned.replace(",", ".")
                    else -> cleaned
                }

            val doubleValue = normalized.toDoubleOrNull() ?: return ZERO
            return fromDouble(doubleValue)
        }

        /** Sums a collection of values. */
        fun sum(values: Iterable<Money>): Money = values.fold(ZERO) { acc, value -> acc + value }
    }
}

/** Converts a [Long] of minor units into a [Money]. */
fun Long.toMoney(): Money = Money.fromMinorUnits(this)

/** Multiplies a [Money] value by this [Long]. */
operator fun Long.times(money: Money): Money = money * this
