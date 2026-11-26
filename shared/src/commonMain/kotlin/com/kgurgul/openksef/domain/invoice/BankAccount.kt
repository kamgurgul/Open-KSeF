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

/**
 * The seller's bank account, written to `Fa/Platnosc/RachunekBankowy` (FA(3) `TRachunekBankowy`).
 *
 * @property number The account number (`NrRB`), already [normalized][normalizeNumber].
 * @property swift The optional SWIFT/BIC code (`SWIFT`), already [normalized][normalizeSwift].
 * @property bankName The optional bank name (`NazwaBanku`).
 */
data class BankAccount(val number: String, val swift: String = "", val bankName: String = "") {

    companion object {
        private const val MIN_NUMBER_LENGTH = 10
        private const val MAX_NUMBER_LENGTH = 34

        /** Number of digits in a Polish NRB, which is an IBAN without the `PL` prefix. */
        private const val POLISH_NRB_LENGTH = 26

        /** `SWIFT` pattern (`SWIFT_Type`). */
        private val SWIFT_REGEX = Regex("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?")

        /** Two letters and two check digits: the start of an IBAN. */
        private val IBAN_PREFIX_REGEX = Regex("[A-Z]{2}[0-9]{2}.*")

        /**
         * Uppercases [raw] and removes the spaces and dashes typed for readability. For example,
         * `pl61-1090` becomes `PL611090`.
         */
        fun normalizeNumber(raw: String): String =
            raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()

        /**
         * True when [raw] is usable as `NrRB`: 10–34 letters or digits. A Polish NRB (26 digits) or
         * an IBAN must also have a valid checksum, which catches mistyped digits.
         */
        fun isValidNumber(raw: String): Boolean {
            val number = normalizeNumber(raw)
            if (number.length !in MIN_NUMBER_LENGTH..MAX_NUMBER_LENGTH) return false
            if (!number.all { it in 'A'..'Z' || it in '0'..'9' }) return false
            return when {
                number.length == POLISH_NRB_LENGTH && number.all { it.isDigit() } ->
                    hasValidIbanChecksum("PL$number")

                IBAN_PREFIX_REGEX.matches(number) -> hasValidIbanChecksum(number)
                else -> true
            }
        }

        fun normalizeSwift(raw: String): String = raw.filterNot { it.isWhitespace() }.uppercase()

        /** True when [raw] is an 8- or 11-character SWIFT/BIC code. */
        fun isValidSwift(raw: String): Boolean = SWIFT_REGEX.matches(normalizeSwift(raw))

        /** ISO 13616 mod-97 check: the rearranged IBAN, read as a number, leaves remainder 1. */
        private fun hasValidIbanChecksum(iban: String): Boolean {
            val rearranged = iban.drop(4) + iban.take(4)
            var remainder = 0
            for (c in rearranged) {
                val value = if (c.isDigit()) c - '0' else c - 'A' + 10
                remainder =
                    if (value < 10) (remainder * 10 + value) % 97
                    else (remainder * 100 + value) % 97
            }
            return remainder == 1
        }
    }
}
