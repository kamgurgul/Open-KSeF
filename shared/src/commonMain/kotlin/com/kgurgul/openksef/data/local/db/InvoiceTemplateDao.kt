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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceTemplateDao {

    @Query("SELECT * FROM invoice_templates ORDER BY name COLLATE NOCASE")
    fun getAll(): Flow<List<InvoiceTemplateEntity>>

    @Upsert suspend fun upsert(template: InvoiceTemplateEntity)

    @Query("DELETE FROM invoice_templates WHERE id = :id") suspend fun delete(id: String)
}
