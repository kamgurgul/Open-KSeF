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

/** How the buyer is identified in `Podmiot2/DaneIdentyfikacyjne`. */
enum class BuyerIdType {
    /** A Polish taxpayer identified by a 10-digit NIP. */
    NIP,

    /** A taxpayer from another EU member state identified by `KodUE` + `NrVatUE`. */
    EU_VAT,
}

/** The buyer's tax identifier written into `Podmiot2/DaneIdentyfikacyjne`. */
sealed interface BuyerTaxId {

    data class Nip(val nip: String) : BuyerTaxId

    /**
     * @property countryCode The member-state code from [EuVat.COUNTRY_CODES], e.g. `IE`.
     * @property number The VAT number without the country prefix, e.g. `6388047V`.
     */
    data class EuVat(val countryCode: String, val number: String) : BuyerTaxId
}

/** EU VAT identification rules of the FA(3) schema. */
object EuVat {

    /**
     * Member-state codes accepted in `KodUE` (`TKodyKrajowUE`), excluding `PL` because Polish
     * buyers are identified by NIP. Greece is `EL`, as in VAT numbers, and `XI` is Northern
     * Ireland.
     */
    val COUNTRY_CODES: List<String> =
        listOf(
            "AT",
            "BE",
            "BG",
            "CY",
            "CZ",
            "DE",
            "DK",
            "EE",
            "EL",
            "ES",
            "FI",
            "FR",
            "HR",
            "HU",
            "IE",
            "IT",
            "LT",
            "LU",
            "LV",
            "MT",
            "NL",
            "PT",
            "RO",
            "SE",
            "SI",
            "SK",
            "XI",
        )

    /** `NrVatUE` pattern (`TNrVatUE`). */
    private val NUMBER_REGEX = Regex("[0-9A-Z+*]{1,12}")

    /**
     * Uppercases [raw], drops separators and strips a leading [countryCode]. For example,
     * `ie-6388047v` becomes `6388047V`.
     */
    fun normalizeNumber(countryCode: String, raw: String): String {
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() || it == '+' || it == '*' }
        return if (countryCode.isNotEmpty()) cleaned.removePrefix(countryCode) else cleaned
    }

    /** True when the [normalizeNumber] result is a valid `NrVatUE`. */
    fun isValidNumber(countryCode: String, raw: String): Boolean =
        NUMBER_REGEX.matches(normalizeNumber(countryCode, raw))

    /**
     * The ISO country code for the buyer's address (`Adres/KodKraju`). VAT numbers use `EL` for
     * Greece, but addresses use `GR`.
     */
    fun addressCountryCode(countryCode: String): String =
        if (countryCode == "EL") "GR" else countryCode
}
