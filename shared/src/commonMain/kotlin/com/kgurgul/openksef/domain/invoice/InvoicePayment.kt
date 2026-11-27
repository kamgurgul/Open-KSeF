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

import com.kgurgul.openksef.domain.money.Money

/** Payment form (`FormaPlatnosci`). */
enum class PaymentMethod(val code: Int) {
    CASH(1),
    CARD(2),
    VOUCHER(3),
    CHEQUE(4),
    CREDIT(5),
    TRANSFER(6),
    MOBILE(7),
}

/** How much of the invoice has been settled, as reported in `Platnosc`. */
sealed interface PaymentStatus {

    data object Unpaid : PaymentStatus

    /** Paid in full (`Zaplacono` + `DataZaplaty`). */
    data class Paid(val date: String) : PaymentStatus

    /** Paid in part (`ZnacznikZaplatyCzesciowej` + `ZaplataCzesciowa`). */
    data class PartiallyPaid(val amount: Money, val date: String) : PaymentStatus
}

/**
 * Payment terms written to `Fa/Platnosc`.
 *
 * @property dueDate The ISO due date (`TerminPlatnosci/Termin`), or blank when there is none.
 * @property bankAccount The account the buyer should pay to (`RachunekBankowy`).
 */
data class InvoicePayment(
    val status: PaymentStatus = PaymentStatus.Unpaid,
    val dueDate: String = "",
    val method: PaymentMethod? = null,
    val bankAccount: BankAccount? = null,
) {
    /** True when there is nothing to write, so the `Platnosc` element is omitted. */
    val isEmpty: Boolean
        get() =
            status == PaymentStatus.Unpaid &&
                dueDate.isBlank() &&
                method == null &&
                bankAccount == null
}
