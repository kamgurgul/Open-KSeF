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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tax rate of an invoice line: the FA(3) `P_12` value (`TStawkaPodatku`).
 *
 * @property code The literal `P_12` value.
 * @property percent The percentage used to calculate the tax amount, which is 0 for zero-rated and
 *   out-of-scope rates.
 */
@Serializable(with = VatRateSerializer::class)
enum class VatRate(val code: String, val percent: Int) {
    RATE_23("23", 23),
    RATE_8("8", 8),
    RATE_5("5", 5),

    /** 0% domestic supply, other than an intra-community supply or export (`0 KR`). */
    ZERO_DOMESTIC("0 KR", 0),

    /** 0% intra-community supply of goods (`0 WDT`). */
    ZERO_INTRA_EU("0 WDT", 0),

    /** 0% export of goods (`0 EX`). */
    ZERO_EXPORT("0 EX", 0),

    /**
     * Supply outside Poland other than the services of art. 100 ust. 1 pkt 4 (`np I`), e.g. B2B
     * services for a buyer established outside the EU.
     */
    NOT_SUBJECT_ABROAD("np I", 0),

    /**
     * Services under art. 100 ust. 1 pkt 4 of the VAT Act (`np II`): B2B services taxed where an EU
     * buyer is established (art. 28b) and reported in VAT-UE. The buyer settles the tax.
     */
    NOT_SUBJECT_EU_SERVICES("np II", 0),

    /** Domestic reverse charge (`oo`): the buyer settles the tax. */
    DOMESTIC_REVERSE_CHARGE("oo", 0);

    /**
     * True when the buyer settles the tax, so the invoice has to carry the "odwrotne obciążenie"
     * annotation (`Adnotacje/P_18`).
     */
    val requiresReverseCharge: Boolean
        get() = this == NOT_SUBJECT_EU_SERVICES || this == DOMESTIC_REVERSE_CHARGE

    companion object {
        val DEFAULT = RATE_23

        /**
         * Resolves a `P_12` code. A bare `"0"` maps to [ZERO_DOMESTIC]: older templates stored it,
         * but FA(3) rejects it as a rate.
         */
        fun fromCode(code: String): VatRate? {
            val trimmed = code.trim()
            if (trimmed == "0") return ZERO_DOMESTIC
            return entries.firstOrNull { it.code == trimmed }
        }
    }
}

/**
 * Serializes a [VatRate] as its `P_12` code. Decoding also accepts the bare JSON numbers (`23`,
 * `8`, `0`) that templates stored before rate codes were supported, and falls back to
 * [VatRate.DEFAULT] for anything unknown so one bad row cannot break the template list.
 */
internal object VatRateSerializer : KSerializer<VatRate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.kgurgul.openksef.VatRate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VatRate) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): VatRate {
        val raw =
            if (decoder is JsonDecoder) {
                decoder.decodeJsonElement().jsonPrimitive.content
            } else {
                decoder.decodeString()
            }
        return VatRate.fromCode(raw) ?: VatRate.DEFAULT
    }
}
