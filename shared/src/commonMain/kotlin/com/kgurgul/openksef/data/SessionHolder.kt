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

class SessionHolder {
    /** Permanent access token (Bearer) returned by /auth/token/redeem — used for all API calls. */
    var accessToken: String? = null

    /** Refresh token used to mint new access tokens via /auth/token/refresh. */
    var refreshToken: String? = null

    /** Reference number of the authentication operation (returned by /auth/ksef-token). */
    var authReferenceNumber: String? = null

    /** Reference number of the currently open online invoice session, if any. */
    var onlineSessionReferenceNumber: String? = null

    var nip: String? = null

    fun clear() {
        accessToken = null
        refreshToken = null
        authReferenceNumber = null
        onlineSessionReferenceNumber = null
        nip = null
    }

    val isActive: Boolean
        get() = accessToken != null
}
