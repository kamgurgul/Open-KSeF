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

package com.kgurgul.openksef.data.repository

import com.kgurgul.openksef.data.local.db.InvoiceTemplateDao
import com.kgurgul.openksef.data.local.db.InvoiceTemplateEntity
import com.kgurgul.openksef.domain.invoice.InvoiceTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [InvoiceTemplateRepository] backed by a Room/SQLite database. */
class RoomInvoiceTemplateRepository(private val dao: InvoiceTemplateDao) :
    InvoiceTemplateRepository {

    override val templates: Flow<List<InvoiceTemplate>> =
        dao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun save(template: InvoiceTemplate) {
        dao.upsert(template.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    private fun InvoiceTemplateEntity.toDomain() =
        InvoiceTemplate(
            id = id,
            name = name,
            buyerNip = buyerNip,
            buyerName = buyerName,
            buyerAddress = buyerAddress,
            items = items,
        )

    private fun InvoiceTemplate.toEntity() =
        InvoiceTemplateEntity(
            id = id,
            name = name,
            buyerNip = buyerNip,
            buyerName = buyerName,
            buyerAddress = buyerAddress,
            items = items,
        )
}
