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
