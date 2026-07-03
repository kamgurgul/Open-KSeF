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

package com.kgurgul.openksef.domain.result

import com.kgurgul.openksef.common.IDispatchersProvider
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.ResultInteractor
import com.kgurgul.openksef.domain.invoice.SellerConfig

/** Persists the user's own seller details. */
class SaveSellerConfigInteractor(
    dispatchersProvider: IDispatchersProvider,
    private val sellerConfigRepository: SellerConfigRepository,
) : ResultInteractor<SellerConfig, Unit>() {

    override val dispatcher = dispatchersProvider.io

    override suspend fun doWork(params: SellerConfig) {
        sellerConfigRepository.save(params)
    }
}
