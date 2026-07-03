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

package com.kgurgul.openksef.ui.settings

import com.kgurgul.openksef.common.TestDispatchersProvider
import com.kgurgul.openksef.data.repository.SellerConfigRepository
import com.kgurgul.openksef.domain.invoice.SellerConfig
import com.kgurgul.openksef.domain.observable.SellerConfigObservable
import com.kgurgul.openksef.domain.result.SaveSellerConfigInteractor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SellerConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_prefillsExistingConfig() = runTest {
        val repository = FakeSellerConfigRepository(SellerConfig(name = "ACME", address = "ul. 1"))
        val viewModel = createViewModel(repository)

        val state = viewModel.uiState.first { it.name.isNotEmpty() }

        assertEquals("ACME", state.name)
        assertEquals("ul. 1", state.address)
    }

    @Test
    fun edits_overridePersistedConfig() = runTest {
        val repository =
            FakeSellerConfigRepository(SellerConfig(name = "Old", address = "ul. Stara 1"))
        val viewModel = createViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.name == "Old" }

        viewModel.onNameChanged("New")

        val state = viewModel.uiState.first { it.name == "New" }
        assertEquals("ul. Stara 1", state.address)
    }

    @Test
    fun onSaveClicked_persistsTrimmedConfigAndEmitsSaved() = runTest {
        val repository = FakeSellerConfigRepository()
        val viewModel = createViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNameChanged("  ACME  ")
        viewModel.onAddressChanged("  ul. Testowa 1  ")
        viewModel.uiState.first { it.name == "  ACME  " && it.address == "  ul. Testowa 1  " }
        viewModel.onSaveClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = repository.config.first()
        assertEquals(SellerConfig(name = "ACME", address = "ul. Testowa 1"), saved)
        assertEquals(SellerConfigEvent.Saved, viewModel.events.first())
    }

    private fun createViewModel(repository: SellerConfigRepository): SellerConfigViewModel {
        val dispatchers = TestDispatchersProvider(testDispatcher)
        return SellerConfigViewModel(
            sellerConfigObservable = SellerConfigObservable(dispatchers, repository),
            saveSellerConfigInteractor = SaveSellerConfigInteractor(dispatchers, repository),
        )
    }

    private class FakeSellerConfigRepository(initial: SellerConfig? = null) :
        SellerConfigRepository {
        private val state = MutableStateFlow(initial)
        override val config: Flow<SellerConfig?> = state

        override suspend fun save(config: SellerConfig) {
            state.value = config
        }
    }
}
