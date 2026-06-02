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

/**
 * Stores the user's own seller details used to pre-fill outgoing invoices. There is at most one
 * row, keyed by the fixed [SINGLE_ROW_ID].
 */
@Entity(tableName = "seller_config")
data class SellerConfigEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val name: String,
    val address: String,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
