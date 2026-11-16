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

package com.kgurgul.openksef.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kgurgul.openksef.common.Constants.DEFAULT_CURRENCY_CODE
import com.kgurgul.openksef.domain.date.DateFormatter
import com.kgurgul.openksef.domain.model.InvoiceKind
import com.kgurgul.openksef.domain.model.InvoiceSummary
import com.kgurgul.openksef.ui.theme.Blue100
import com.kgurgul.openksef.ui.theme.Blue600
import com.kgurgul.openksef.ui.theme.ErrorContainerRed
import com.kgurgul.openksef.ui.theme.ErrorRed
import com.kgurgul.openksef.ui.theme.Neutral100
import com.kgurgul.openksef.ui.theme.Neutral600
import com.kgurgul.openksef.ui.theme.Success
import com.kgurgul.openksef.ui.theme.SuccessContainer
import com.kgurgul.openksef.ui.theme.Warning
import com.kgurgul.openksef.ui.theme.WarningContainer
import com.kgurgul.openksef.ui.theme.jetBrainsMonoFamily
import com.kgurgul.openksef.ui.theme.spaceGroteskFamily
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.amount_with_currency
import openksef.shared.generated.resources.invoice_card_buyer
import openksef.shared.generated.resources.invoice_card_gross
import openksef.shared.generated.resources.invoice_card_net
import openksef.shared.generated.resources.invoice_card_seller
import openksef.shared.generated.resources.invoice_card_vat
import openksef.shared.generated.resources.invoice_kind_advance
import openksef.shared.generated.resources.invoice_kind_correction
import openksef.shared.generated.resources.invoice_kind_settlement
import openksef.shared.generated.resources.invoice_kind_simplified
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoadingOverlay(isLoading: Boolean) {
    if (isLoading) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(enabled = false) {},
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceCard(invoice: InvoiceSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InvoiceAvatar(invoice.sellerName.ifBlank { invoice.sellerNip })
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = invoice.invoiceNumber.ifBlank { invoice.ksefReferenceNumber },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        invoice.kind.labelResource()?.let { label ->
                            Spacer(modifier = Modifier.width(8.dp))
                            InvoiceKindBadge(
                                text = stringResource(label),
                                isCorrection = invoice.kind == InvoiceKind.CORRECTION,
                            )
                        }
                    }
                    Text(
                        text =
                            stringResource(
                                Res.string.invoice_card_seller,
                                invoice.sellerName.ifBlank { invoice.sellerNip },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            stringResource(
                                Res.string.invoice_card_buyer,
                                invoice.buyerName.ifBlank { invoice.buyerNip },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        DateFormatter.format(invoice.issueDate.ifBlank { invoice.invoicingDate }),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFamily = jetBrainsMonoFamily()
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AmountColumn(
                    label = stringResource(Res.string.invoice_card_net),
                    value = invoice.net.toFormattedString(),
                    currency = invoice.currency,
                )
                // KSeF always reports the VAT total in PLN, whatever the invoice currency is.
                AmountColumn(
                    label = stringResource(Res.string.invoice_card_vat),
                    value = invoice.vat.toFormattedString(),
                    currency = DEFAULT_CURRENCY_CODE,
                )
                AmountColumn(
                    label = stringResource(Res.string.invoice_card_gross),
                    value = invoice.gross.toFormattedString(),
                    currency = invoice.currency,
                    emphasized = true,
                )
            }
        }
    }
}

private fun InvoiceKind.labelResource(): StringResource? =
    when (this) {
        InvoiceKind.STANDARD -> null
        InvoiceKind.CORRECTION -> Res.string.invoice_kind_correction
        InvoiceKind.ADVANCE -> Res.string.invoice_kind_advance
        InvoiceKind.SETTLEMENT -> Res.string.invoice_kind_settlement
        InvoiceKind.SIMPLIFIED -> Res.string.invoice_kind_simplified
    }

@Composable
private fun InvoiceKindBadge(text: String, isCorrection: Boolean) {
    val container =
        if (isCorrection) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer
    val content =
        if (isCorrection) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(container)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun AmountColumn(
    label: String,
    value: String,
    currency: String,
    emphasized: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.amount_with_currency, value, currency),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = jetBrainsMonoFamily(),
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color =
                if (emphasized) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * Avatar color pairs derived from the design-system semantic status palette. The background/content
 * pair is picked deterministically from the initials so the same contractor always renders in the
 * same color.
 */
private data class AvatarColors(val container: Color, val content: Color)

private val avatarPalette =
    listOf(
        AvatarColors(Blue100, Blue600),
        AvatarColors(SuccessContainer, Success),
        AvatarColors(WarningContainer, Warning),
        AvatarColors(ErrorContainerRed, ErrorRed),
        AvatarColors(Neutral100, Neutral600),
    )

@Composable
private fun InvoiceAvatar(name: String) {
    val initials = name.toInitials()
    val colors = avatarPalette[initials.colorIndex(avatarPalette.size)]
    Box(
        modifier =
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontFamily = spaceGroteskFamily(),
                    fontWeight = FontWeight.Bold,
                ),
            color = colors.content,
            textAlign = TextAlign.Center,
        )
    }
}

private fun String.colorIndex(size: Int): Int {
    // Stable, non-negative index regardless of hashCode sign (incl. Int.MIN_VALUE).
    return ((hashCode() % size) + size) % size
}

private fun String.toInitials(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
