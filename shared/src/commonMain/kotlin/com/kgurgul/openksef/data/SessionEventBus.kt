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

package com.kgurgul.openksef.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App-wide bus for session-expiry signals.
 *
 * The KSeF API client publishes here whenever a request fails because the session can no longer be
 * refreshed. A single observer — `MainViewModel` on the root screen — collects [sessionExpired] and
 * triggers the redirect to the login screen, so individual screens no longer handle this on their
 * own.
 */
class SessionEventBus {

    private val sessionExpiredChannel = Channel<Unit>(Channel.CONFLATED)

    /** Cold flow of session-expiry signals, intended to be collected once by `MainViewModel`. */
    val sessionExpired: Flow<Unit> = sessionExpiredChannel.receiveAsFlow()

    /** Signals that the current session is no longer valid and the user must re-authenticate. */
    fun notifySessionExpired() {
        sessionExpiredChannel.trySend(Unit)
    }
}
