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

package com.kgurgul.openksef.ui.sendinvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.common.toUiText
import com.kgurgul.openksef.domain.invoice.BankAccount
import com.kgurgul.openksef.domain.invoice.BuyerIdType
import com.kgurgul.openksef.domain.invoice.BuyerTaxId
import com.kgurgul.openksef.domain.invoice.EuVat
import com.kgurgul.openksef.domain.invoice.InvoiceBuilder
import com.kgurgul.openksef.domain.invoice.InvoiceData
import com.kgurgul.openksef.domain.invoice.InvoiceLineItem
import com.kgurgul.openksef.domain.invoice.InvoicePayment
import com.kgurgul.openksef.domain.invoice.InvoiceTemplate
import com.kgurgul.openksef.domain.invoice.InvoiceTemplateItem
import com.kgurgul.openksef.domain.invoice.PaymentMethod
import com.kgurgul.openksef.domain.invoice.PaymentStatus
import com.kgurgul.openksef.domain.invoice.VatRate
import com.kgurgul.openksef.domain.invoke
import com.kgurgul.openksef.domain.money.Money
import com.kgurgul.openksef.domain.observable.InvoiceTemplatesObservable
import com.kgurgul.openksef.domain.observable.SellerConfigObservable
import com.kgurgul.openksef.domain.observe
import com.kgurgul.openksef.domain.result.DeleteInvoiceTemplateInteractor
import com.kgurgul.openksef.domain.result.GetSessionNipInteractor
import com.kgurgul.openksef.domain.result.SaveInvoiceTemplateInteractor
import com.kgurgul.openksef.domain.result.SendInvoiceInteractor
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_bank_account_invalid
import openksef.shared.generated.resources.error_eu_vat_invalid
import openksef.shared.generated.resources.error_form_invalid
import openksef.shared.generated.resources.error_issue_date_invalid
import openksef.shared.generated.resources.error_items_required
import openksef.shared.generated.resources.error_nip_invalid
import openksef.shared.generated.resources.error_paid_amount_invalid
import openksef.shared.generated.resources.error_required
import openksef.shared.generated.resources.error_send_invoice
import openksef.shared.generated.resources.error_swift_invalid

data class InvoiceLineItemUi(
    val description: String = "",
    val quantity: String = "1",
    val unit: String = "szt.",
    val unitPrice: String = "",
    val vatRate: VatRate = VatRate.DEFAULT,
    val netValue: Money = Money.ZERO,
    val grossValue: Money = Money.ZERO,
)

/** The payment state the user picks; [PARTIALLY_PAID] also asks for the amount paid. */
enum class PaymentStatusOption {
    UNPAID,
    PAID,
    PARTIALLY_PAID,
}

data class SendInvoiceUiState(
    val sellerNip: String = "",
    val sellerName: String = "",
    val sellerAddress: String = "",
    val buyerIdType: BuyerIdType = BuyerIdType.NIP,
    val buyerNip: String = "",
    val buyerEuCountryCode: String = "",
    val buyerEuVatNumber: String = "",
    val buyerName: String = "",
    val buyerAddress: String = "",
    val invoiceNumber: String = "",
    val issueDate: String = "",
    val items: List<InvoiceLineItemUi> = listOf(InvoiceLineItemUi()),
    /** The user's reverse-charge choice; it is ignored while [isReverseChargeRequired]. */
    val reverseCharge: Boolean = false,
    /** An item's VAT rate requires the reverse-charge annotation, so it cannot be turned off. */
    val isReverseChargeRequired: Boolean = false,
    val paymentMethod: PaymentMethod = PaymentMethod.TRANSFER,
    val paymentStatus: PaymentStatusOption = PaymentStatusOption.UNPAID,
    val paymentDate: String = "",
    val paidAmount: String = "",
    val dueDate: String = "",
    val bankAccount: String = "",
    val bankSwift: String = "",
    val bankName: String = "",
    val templates: List<InvoiceTemplate> = emptyList(),
    val selectedTemplateId: String? = null,
    val currency: String = "PLN",
    val isLoading: Boolean = false,
    val isSent: Boolean = false,
    val sentReferenceNumber: String = "",
    val sentKsefNumber: String = "",
    val error: UiText? = null,
    val validationErrors: Map<String, UiText> = emptyMap(),
) {
    /** Items that are sent: the form keeps rows without a description, which are skipped. */
    val billableItems: List<InvoiceLineItemUi>
        get() = items.filter { it.description.isNotBlank() }
}

class SendInvoiceViewModel(
    private val sendInvoiceInteractor: SendInvoiceInteractor,
    getSessionNipInteractor: GetSessionNipInteractor,
    invoiceTemplatesObservable: InvoiceTemplatesObservable,
    sellerConfigObservable: SellerConfigObservable,
    private val saveInvoiceTemplateInteractor: SaveInvoiceTemplateInteractor,
    private val deleteInvoiceTemplateInteractor: DeleteInvoiceTemplateInteractor,
) : ViewModel() {

    private val formState =
        MutableStateFlow(
            SendInvoiceUiState(
                issueDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            )
        )

    /** Seller edits; `null` fields fall back to the persisted seller config. */
    private val sellerEdits = MutableStateFlow(SellerEdits())
    private val sessionNip = MutableStateFlow("")

    val uiState: StateFlow<SendInvoiceUiState> =
        combine(
                formState,
                sellerEdits,
                sessionNip,
                invoiceTemplatesObservable.observe(),
                sellerConfigObservable.observe(),
            ) { form, edits, nip, templates, config ->
                form.copy(
                    sellerNip = nip,
                    sellerName = edits.name ?: config?.name ?: "",
                    sellerAddress = edits.address ?: config?.address ?: "",
                    bankAccount = edits.bankAccount ?: config?.bankAccount ?: "",
                    bankSwift = edits.bankSwift ?: config?.bankSwift ?: "",
                    bankName = edits.bankName ?: config?.bankName ?: "",
                    isReverseChargeRequired = form.items.any { it.vatRate.requiresReverseCharge },
                    templates = templates,
                    selectedTemplateId =
                        form.selectedTemplateId.takeIf { id -> templates.any { it.id == id } },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SendInvoiceUiState())

    init {
        viewModelScope.launch { sessionNip.value = getSessionNipInteractor() ?: "" }
    }

    fun onSellerNameChanged(name: String) {
        sellerEdits.update { it.copy(name = name) }
        clearValidationErrors(FIELD_SELLER_NAME)
    }

    fun onSellerAddressChanged(address: String) {
        sellerEdits.update { it.copy(address = address) }
        clearValidationErrors(FIELD_SELLER_ADDRESS)
    }

    fun onBuyerIdTypeChanged(type: BuyerIdType) {
        formState.update { it.copy(buyerIdType = type) }
        clearValidationErrors(FIELD_BUYER_NIP, FIELD_BUYER_EU_COUNTRY, FIELD_BUYER_EU_VAT)
    }

    fun onBuyerNipChanged(nip: String) {
        formState.update { it.copy(buyerNip = nip) }
        clearValidationErrors(FIELD_BUYER_NIP)
    }

    fun onBuyerEuCountryChanged(countryCode: String) {
        formState.update { it.copy(buyerEuCountryCode = countryCode) }
        clearValidationErrors(FIELD_BUYER_EU_COUNTRY, FIELD_BUYER_EU_VAT)
    }

    fun onBuyerEuVatNumberChanged(number: String) {
        formState.update { it.copy(buyerEuVatNumber = number) }
        clearValidationErrors(FIELD_BUYER_EU_VAT)
    }

    fun onBuyerNameChanged(name: String) {
        formState.update { it.copy(buyerName = name) }
        clearValidationErrors(FIELD_BUYER_NAME)
    }

    fun onBuyerAddressChanged(address: String) {
        formState.update { it.copy(buyerAddress = address) }
    }

    fun onInvoiceNumberChanged(number: String) {
        formState.update { it.copy(invoiceNumber = number) }
        clearValidationErrors(FIELD_INVOICE_NUMBER)
    }

    fun onIssueDateChanged(date: String) {
        formState.update { it.copy(issueDate = date) }
        clearValidationErrors(FIELD_ISSUE_DATE)
    }

    fun onReverseChargeChanged(enabled: Boolean) {
        formState.update { it.copy(reverseCharge = enabled) }
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        formState.update { it.copy(paymentMethod = method) }
        clearValidationErrors(FIELD_BANK_ACCOUNT, FIELD_BANK_SWIFT)
    }

    fun onPaymentStatusChanged(status: PaymentStatusOption) {
        formState.update { state ->
            state.copy(
                paymentStatus = status,
                // Suggest the issue date, the usual payment date for invoices paid up front.
                paymentDate =
                    if (status != PaymentStatusOption.UNPAID && state.paymentDate.isBlank()) {
                        state.issueDate
                    } else {
                        state.paymentDate
                    },
            )
        }
        clearValidationErrors(FIELD_PAYMENT_DATE, FIELD_PAID_AMOUNT)
    }

    fun onPaymentDateChanged(date: String) {
        formState.update { it.copy(paymentDate = date) }
        clearValidationErrors(FIELD_PAYMENT_DATE)
    }

    fun onPaidAmountChanged(amount: String) {
        formState.update { it.copy(paidAmount = amount) }
        clearValidationErrors(FIELD_PAID_AMOUNT)
    }

    fun onDueDateChanged(date: String) {
        formState.update { it.copy(dueDate = date) }
        clearValidationErrors(FIELD_DUE_DATE)
    }

    fun onBankAccountChanged(bankAccount: String) {
        sellerEdits.update { it.copy(bankAccount = bankAccount) }
        clearValidationErrors(FIELD_BANK_ACCOUNT)
    }

    fun onBankSwiftChanged(bankSwift: String) {
        sellerEdits.update { it.copy(bankSwift = bankSwift) }
        clearValidationErrors(FIELD_BANK_SWIFT)
    }

    fun onBankNameChanged(bankName: String) {
        sellerEdits.update { it.copy(bankName = bankName) }
    }

    fun addItem() {
        formState.update { it.copy(items = it.items + InvoiceLineItemUi()) }
    }

    fun removeItem(index: Int) {
        formState.update { state ->
            if (state.items.size > 1) {
                state.copy(items = state.items.toMutableList().apply { removeAt(index) })
            } else state
        }
    }

    fun updateItem(index: Int, item: InvoiceLineItemUi) {
        val updated = withCalculatedValues(item)

        formState.update { state ->
            state.copy(
                items = state.items.toMutableList().apply { set(index, updated) },
                validationErrors = state.validationErrors - FIELD_ITEMS - FIELD_PAID_AMOUNT,
            )
        }
    }

    fun onTemplateSelected(template: InvoiceTemplate) {
        val items =
            template.items.map { templateItem ->
                withCalculatedValues(
                    InvoiceLineItemUi(
                        description = templateItem.description,
                        quantity = templateItem.quantity,
                        unit = templateItem.unit,
                        unitPrice = templateItem.unitPrice,
                        vatRate = templateItem.vatRate,
                    )
                )
            }
        val hasBuyerId = template.buyerNip.isNotBlank() || template.buyerEuVatNumber.isNotBlank()

        formState.update { state ->
            state.copy(
                selectedTemplateId = template.id,
                buyerIdType = if (hasBuyerId) template.buyerIdType else state.buyerIdType,
                buyerNip = template.buyerNip.ifBlank { state.buyerNip },
                buyerEuCountryCode =
                    template.buyerEuCountryCode.ifBlank { state.buyerEuCountryCode },
                buyerEuVatNumber = template.buyerEuVatNumber.ifBlank { state.buyerEuVatNumber },
                buyerName = template.buyerName.ifBlank { state.buyerName },
                buyerAddress = template.buyerAddress.ifBlank { state.buyerAddress },
                items = items.ifEmpty { listOf(InvoiceLineItemUi()) },
                validationErrors = emptyMap(),
            )
        }
    }

    fun onSaveTemplate(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val state = uiState.value
        val template =
            InvoiceTemplate(
                id = Clock.System.now().toEpochMilliseconds().toString(),
                name = trimmedName,
                buyerIdType = state.buyerIdType,
                buyerNip = state.buyerNip,
                buyerEuCountryCode = state.buyerEuCountryCode,
                buyerEuVatNumber = state.buyerEuVatNumber,
                buyerName = state.buyerName,
                buyerAddress = state.buyerAddress,
                items =
                    state.items.map { item ->
                        InvoiceTemplateItem(
                            description = item.description,
                            quantity = item.quantity,
                            unit = item.unit,
                            unitPrice = item.unitPrice,
                            vatRate = item.vatRate,
                        )
                    },
            )
        viewModelScope.launch { saveInvoiceTemplateInteractor(template) }
    }

    fun onDeleteTemplate(id: String) {
        viewModelScope.launch { deleteInvoiceTemplateInteractor(id) }
    }

    private fun withCalculatedValues(item: InvoiceLineItemUi): InvoiceLineItemUi {
        val qty = item.quantity.toDoubleOrNull() ?: 0.0
        val unitPrice = Money.fromFormattedString(item.unitPrice)
        val net = unitPrice * qty
        val gross = net.withVatRate(item.vatRate.percent)
        return item.copy(netValue = net, grossValue = gross)
    }

    fun send() {
        val state = uiState.value
        val errors = validate(state)

        if (errors.isNotEmpty()) {
            formState.update {
                it.copy(
                    validationErrors = errors,
                    error = UiText.Resource(Res.string.error_form_invalid),
                )
            }
            return
        }

        formState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val xml = InvoiceBuilder.buildXml(state.toInvoiceData())
            sendInvoiceInteractor(xml)
                .onSuccess { result ->
                    formState.update {
                        it.copy(
                            isLoading = false,
                            isSent = true,
                            sentReferenceNumber = result.referenceNumber,
                            sentKsefNumber = result.ksefNumber ?: "",
                        )
                    }
                }
                .onFailure { e ->
                    formState.update {
                        it.copy(
                            isLoading = false,
                            error = e.toUiText(Res.string.error_send_invoice),
                        )
                    }
                }
        }
    }

    fun clearError() {
        formState.update { it.copy(error = null) }
    }

    private fun validate(state: SendInvoiceUiState): Map<String, UiText> {
        val errors = mutableMapOf<String, UiText>()
        val required = UiText.Resource(Res.string.error_required)
        val invalidDate = UiText.Resource(Res.string.error_issue_date_invalid)

        if (state.sellerName.isBlank()) {
            errors[FIELD_SELLER_NAME] = required
        }
        // FA(3) requires the seller address element.
        if (state.sellerAddress.isBlank()) {
            errors[FIELD_SELLER_ADDRESS] = required
        }
        when (state.buyerIdType) {
            BuyerIdType.NIP ->
                if (state.buyerNip.length != 10 || !state.buyerNip.all { it.isDigit() }) {
                    errors[FIELD_BUYER_NIP] = UiText.Resource(Res.string.error_nip_invalid)
                }

            BuyerIdType.EU_VAT -> {
                if (state.buyerEuCountryCode !in EuVat.COUNTRY_CODES) {
                    errors[FIELD_BUYER_EU_COUNTRY] = required
                }
                if (!EuVat.isValidNumber(state.buyerEuCountryCode, state.buyerEuVatNumber)) {
                    errors[FIELD_BUYER_EU_VAT] = UiText.Resource(Res.string.error_eu_vat_invalid)
                }
            }
        }
        if (state.buyerName.isBlank()) {
            errors[FIELD_BUYER_NAME] = required
        }
        if (state.invoiceNumber.isBlank()) {
            errors[FIELD_INVOICE_NUMBER] = required
        }
        // FA(3) requires ISO-8601 dates (YYYY-MM-DD).
        if (!isValidIsoDate(state.issueDate)) {
            errors[FIELD_ISSUE_DATE] = invalidDate
        }
        if (state.billableItems.isEmpty()) {
            errors[FIELD_ITEMS] = UiText.Resource(Res.string.error_items_required)
        }

        if (state.dueDate.isNotBlank() && !isValidIsoDate(state.dueDate)) {
            errors[FIELD_DUE_DATE] = invalidDate
        }
        if (
            state.paymentStatus != PaymentStatusOption.UNPAID && !isValidIsoDate(state.paymentDate)
        ) {
            errors[FIELD_PAYMENT_DATE] = invalidDate
        }
        if (state.paymentStatus == PaymentStatusOption.PARTIALLY_PAID) {
            // A partial payment covering the whole gross amount is a full payment.
            val paid = Money.fromFormattedString(state.paidAmount)
            val totalGross = Money.sum(state.billableItems.map { it.grossValue })
            if (!paid.isPositive || paid >= totalGross) {
                errors[FIELD_PAID_AMOUNT] = UiText.Resource(Res.string.error_paid_amount_invalid)
            }
        }
        if (state.paymentMethod == PaymentMethod.TRANSFER) {
            if (state.bankAccount.isNotBlank() && !BankAccount.isValidNumber(state.bankAccount)) {
                errors[FIELD_BANK_ACCOUNT] = UiText.Resource(Res.string.error_bank_account_invalid)
            }
            if (state.bankSwift.isNotBlank() && !BankAccount.isValidSwift(state.bankSwift)) {
                errors[FIELD_BANK_SWIFT] = UiText.Resource(Res.string.error_swift_invalid)
            }
        }
        return errors
    }

    private fun SendInvoiceUiState.toInvoiceData(): InvoiceData {
        val buyerTaxId =
            when (buyerIdType) {
                BuyerIdType.NIP -> BuyerTaxId.Nip(buyerNip)
                BuyerIdType.EU_VAT ->
                    BuyerTaxId.EuVat(
                        countryCode = buyerEuCountryCode,
                        number = EuVat.normalizeNumber(buyerEuCountryCode, buyerEuVatNumber),
                    )
            }
        val status =
            when (paymentStatus) {
                PaymentStatusOption.UNPAID -> PaymentStatus.Unpaid
                PaymentStatusOption.PAID -> PaymentStatus.Paid(paymentDate)
                PaymentStatusOption.PARTIALLY_PAID ->
                    PaymentStatus.PartiallyPaid(Money.fromFormattedString(paidAmount), paymentDate)
            }
        // The account is only relevant to the buyer when paying by transfer.
        val account =
            if (paymentMethod == PaymentMethod.TRANSFER && bankAccount.isNotBlank()) {
                BankAccount(
                    number = BankAccount.normalizeNumber(bankAccount),
                    swift = BankAccount.normalizeSwift(bankSwift),
                    bankName = bankName.trim(),
                )
            } else {
                null
            }
        return InvoiceData(
            invoiceNumber = invoiceNumber,
            issueDate = issueDate,
            sellerNip = sellerNip,
            sellerName = sellerName,
            sellerAddress = sellerAddress,
            buyerTaxId = buyerTaxId,
            buyerName = buyerName,
            buyerAddress = buyerAddress,
            currency = currency,
            items =
                billableItems.map { item ->
                    InvoiceLineItem(
                        description = item.description,
                        quantity = item.quantity.toDoubleOrNull() ?: 0.0,
                        unit = item.unit.ifBlank { "szt." },
                        unitPrice = Money.fromFormattedString(item.unitPrice),
                        vatRate = item.vatRate,
                        netValue = item.netValue,
                        grossValue = item.grossValue,
                    )
                },
            reverseCharge = reverseCharge || isReverseChargeRequired,
            payment =
                InvoicePayment(
                    status = status,
                    dueDate = dueDate,
                    method = paymentMethod,
                    bankAccount = account,
                ),
        )
    }

    private fun clearValidationErrors(vararg fields: String) {
        formState.update { it.copy(validationErrors = it.validationErrors - fields.toSet()) }
    }

    private fun isValidIsoDate(date: String): Boolean = runCatching {
        LocalDate.parse(date)
    }
        .isSuccess

    private data class SellerEdits(
        val name: String? = null,
        val address: String? = null,
        val bankAccount: String? = null,
        val bankSwift: String? = null,
        val bankName: String? = null,
    )

    companion object {
        const val FIELD_SELLER_NAME = "sellerName"
        const val FIELD_SELLER_ADDRESS = "sellerAddress"
        const val FIELD_BUYER_NIP = "buyerNip"
        const val FIELD_BUYER_EU_COUNTRY = "buyerEuCountry"
        const val FIELD_BUYER_EU_VAT = "buyerEuVat"
        const val FIELD_BUYER_NAME = "buyerName"
        const val FIELD_INVOICE_NUMBER = "invoiceNumber"
        const val FIELD_ISSUE_DATE = "issueDate"
        const val FIELD_ITEMS = "items"
        const val FIELD_DUE_DATE = "dueDate"
        const val FIELD_PAYMENT_DATE = "paymentDate"
        const val FIELD_PAID_AMOUNT = "paidAmount"
        const val FIELD_BANK_ACCOUNT = "bankAccount"
        const val FIELD_BANK_SWIFT = "bankSwift"
    }
}
