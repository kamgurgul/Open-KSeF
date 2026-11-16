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
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.domain.model.KsefEnvironment
import com.kgurgul.openksef.domain.result.GetSessionEnvironmentInteractor
import com.kgurgul.openksef.domain.result.GetSessionNipInteractor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
    fun uiState_showsNipAndEnvironmentOfTheSession() = runTest {
        val sessionHolder =
            SessionHolder().apply {
                update(
                    accessToken = "token",
                    nip = "1234567890",
                    baseUrl = KsefEnvironment.DEMO.baseUrl,
                )
            }
        val viewModel = createViewModel(sessionHolder)

        val state = viewModel.uiState.first { it.nip.isNotEmpty() }

        assertEquals("1234567890", state.nip)
        assertEquals(KsefEnvironment.DEMO, state.environment)
    }

    @Test
    fun uiState_withoutSession_hasNoNipAndDefaultEnvironment() = runTest {
        val viewModel = createViewModel(SessionHolder())

        val state = viewModel.uiState.first { it.environment != null }

        assertEquals("", state.nip)
        assertEquals(KsefEnvironment.TEST, state.environment)
    }

    @Test
    fun uiState_unknownBaseUrl_hasNoEnvironment() = runTest {
        val sessionHolder =
            SessionHolder().apply { update(nip = "1234567890", baseUrl = "https://example.test") }
        val viewModel = createViewModel(sessionHolder)
        backgroundScope.launch { viewModel.uiState.collect {} }

        val state = viewModel.uiState.first { it.nip.isNotEmpty() }

        assertNull(state.environment)
    }

    private fun createViewModel(sessionHolder: SessionHolder): SettingsViewModel {
        val dispatchers = TestDispatchersProvider(testDispatcher)
        return SettingsViewModel(
            getSessionNipInteractor = GetSessionNipInteractor(dispatchers, sessionHolder),
            getSessionEnvironmentInteractor =
                GetSessionEnvironmentInteractor(dispatchers, sessionHolder),
        )
    }
}
