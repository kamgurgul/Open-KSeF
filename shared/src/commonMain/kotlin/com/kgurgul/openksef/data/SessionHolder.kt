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

import com.kgurgul.openksef.domain.model.KsefEnvironment
import kotlin.concurrent.Volatile

/**
 * In-memory holder for the current KSeF session.
 *
 * The mutable state is kept private behind an immutable [State] snapshot held in a single
 * [Volatile] field. Reads are exposed as read-only properties and always see a consistent snapshot;
 * writes go through [update] (which replaces several fields at once) or [clear]. Because the
 * snapshot reference is swapped atomically, callers never observe torn state.
 */
class SessionHolder {

    @Volatile private var state = State()

    /** Permanent access token (Bearer) returned by /auth/token/redeem — used for all API calls. */
    val accessToken: String?
        get() = state.accessToken

    /** Refresh token used to mint new access tokens via /auth/token/refresh. */
    val refreshToken: String?
        get() = state.refreshToken

    /** Reference number of the authentication operation (returned by /auth/ksef-token). */
    val authReferenceNumber: String?
        get() = state.authReferenceNumber

    /** Reference number of the currently open online invoice session, if any. */
    val onlineSessionReferenceNumber: String?
        get() = state.onlineSessionReferenceNumber

    val nip: String?
        get() = state.nip

    /**
     * Base URL of the currently selected KSeF environment (used by the Auth plugin for refresh).
     */
    val baseUrl: String
        get() = state.baseUrl

    val isActive: Boolean
        get() = state.accessToken != null

    /**
     * Atomically updates the session. Every argument defaults to its current value, so a caller
     * changes as many fields as needed in a single call without disturbing the rest.
     */
    fun update(
        accessToken: String? = state.accessToken,
        refreshToken: String? = state.refreshToken,
        authReferenceNumber: String? = state.authReferenceNumber,
        onlineSessionReferenceNumber: String? = state.onlineSessionReferenceNumber,
        nip: String? = state.nip,
        baseUrl: String = state.baseUrl,
    ) {
        state =
            State(
                accessToken = accessToken,
                refreshToken = refreshToken,
                authReferenceNumber = authReferenceNumber,
                onlineSessionReferenceNumber = onlineSessionReferenceNumber,
                nip = nip,
                baseUrl = baseUrl,
            )
    }

    /** Clears all session data while keeping the selected environment [baseUrl]. */
    fun clear() {
        state = State(baseUrl = state.baseUrl)
    }

    private data class State(
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val authReferenceNumber: String? = null,
        val onlineSessionReferenceNumber: String? = null,
        val nip: String? = null,
        val baseUrl: String = KsefEnvironment.TEST.baseUrl,
    )
}
