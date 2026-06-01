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

package com.kgurgul.openksef.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.kgurgul.openksef.domain.invoice.InvoiceTemplateItem
import kotlinx.serialization.json.Json

@Entity(tableName = "invoice_templates")
data class InvoiceTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val buyerNip: String,
    val buyerName: String,
    val buyerAddress: String,
    val items: List<InvoiceTemplateItem>,
)

/** Stores the line items of a template as a JSON column. */
class InvoiceTemplateItemConverter {

    @TypeConverter
    fun fromItems(items: List<InvoiceTemplateItem>): String = Json.encodeToString(items)

    @TypeConverter
    fun toItems(raw: String): List<InvoiceTemplateItem> =
        if (raw.isBlank()) emptyList() else Json.decodeFromString(raw)
}
