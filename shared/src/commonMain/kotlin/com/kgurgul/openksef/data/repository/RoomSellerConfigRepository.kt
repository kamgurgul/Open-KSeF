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

import com.kgurgul.openksef.data.local.db.SellerConfigDao
import com.kgurgul.openksef.data.local.db.SellerConfigEntity
import com.kgurgul.openksef.domain.invoice.SellerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SellerConfigRepository] backed by a Room/SQLite database. */
class RoomSellerConfigRepository(private val dao: SellerConfigDao) : SellerConfigRepository {

    override val config: Flow<SellerConfig?> =
        dao.get().map { entity -> entity?.let { SellerConfig(name = it.name, address = it.address) } }

    override suspend fun save(config: SellerConfig) {
        dao.upsert(SellerConfigEntity(name = config.name, address = config.address))
    }
}
