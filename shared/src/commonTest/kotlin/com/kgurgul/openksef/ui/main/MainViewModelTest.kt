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

import com.kgurgul.openksef.common.TestDispatchersProvider
import com.kgurgul.openksef.data.SessionEventBus
import com.kgurgul.openksef.domain.observable.SessionExpiredObservable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class MainViewModelTest {

    @Test
    fun sessionExpired_emitsSessionExpiredEvent() = runTest {
        val sessionEventBus = SessionEventBus()
        val viewModel = createViewModel(sessionEventBus)

        sessionEventBus.notifySessionExpired()

        assertIs<MainEvent.SessionExpired>(viewModel.events.first())
    }

    @Test
    fun sessionExpired_signalledBeforeCollection_isStillDelivered() = runTest {
        // The channel is buffered, so a signal raised before collection is not lost.
        val sessionEventBus = SessionEventBus()
        sessionEventBus.notifySessionExpired()

        val viewModel = createViewModel(sessionEventBus)

        assertIs<MainEvent.SessionExpired>(viewModel.events.first())
    }

    @Test
    fun multipleSessionExpired_signalsEmitMultipleEvents() = runTest {
        val sessionEventBus = SessionEventBus()
        val viewModel = createViewModel(sessionEventBus)

        sessionEventBus.notifySessionExpired()
        val event = viewModel.events.first()

        assertEquals(MainEvent.SessionExpired, event)
    }

    private fun createViewModel(sessionEventBus: SessionEventBus): MainViewModel =
        MainViewModel(
            SessionExpiredObservable(
                TestDispatchersProvider(Dispatchers.Unconfined),
                sessionEventBus,
            )
        )
}
