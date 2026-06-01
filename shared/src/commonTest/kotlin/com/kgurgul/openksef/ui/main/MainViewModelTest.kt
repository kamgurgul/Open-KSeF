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

package com.kgurgul.openksef.ui.main

import com.kgurgul.openksef.data.SessionEventBus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class MainViewModelTest {

    @Test
    fun sessionExpired_setsSessionExpiredState() = runTest {
        val sessionEventBus = SessionEventBus()
        val viewModel = MainViewModel(sessionEventBus)

        sessionEventBus.notifySessionExpired()

        assertTrue(viewModel.uiState.first { it.sessionExpired }.sessionExpired)
    }

    @Test
    fun sessionExpired_signalledBeforeCollection_isStillDelivered() = runTest {
        // The bus is conflated, so a signal raised before MainViewModel collects is not lost.
        val sessionEventBus = SessionEventBus()
        sessionEventBus.notifySessionExpired()

        val viewModel = MainViewModel(sessionEventBus)

        assertTrue(viewModel.uiState.first { it.sessionExpired }.sessionExpired)
    }

    @Test
    fun onSessionExpiryHandled_clearsState() = runTest {
        val sessionEventBus = SessionEventBus()
        val viewModel = MainViewModel(sessionEventBus)
        sessionEventBus.notifySessionExpired()
        viewModel.uiState.first { it.sessionExpired }

        viewModel.onSessionExpiryHandled()

        assertFalse(viewModel.uiState.value.sessionExpired)
    }
}
