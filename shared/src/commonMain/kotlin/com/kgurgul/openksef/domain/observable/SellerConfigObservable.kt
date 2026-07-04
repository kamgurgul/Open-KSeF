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

package com.kgurgul.openksef.domain.observable

import com.kgurgul.openksef.common.IDispatchersProvider
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.ImmutableInteractor
import com.kgurgul.openksef.domain.invoice.SellerConfig
import kotlinx.coroutines.flow.Flow

/** Emits the saved seller config, or `null` when it has never been set. */
class SellerConfigObservable(
    dispatchersProvider: IDispatchersProvider,
    private val sellerConfigRepository: SellerConfigRepository,
) : ImmutableInteractor<Unit, SellerConfig?>() {

    override val dispatcher = dispatchersProvider.io

    override fun createObservable(params: Unit): Flow<SellerConfig?> = sellerConfigRepository.config
}
