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

package com.kgurgul.openksef.data.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kgurgul.openksef.data.SessionEventBus
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.local.SecureTokenStorage
import com.kgurgul.openksef.data.local.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem

class KsefApiClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonHeaders =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private object FakeCrypto : KsefCrypto {
        override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data

        override fun secureRandomBytes(size: Int) = ByteArray(size)

        override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray) = data
    }

    /**
     * MockEngine simulating an expired session: the protected endpoint accepts only the token
     * issued by the (re-)authentication flow, and the refresh endpoint always rejects.
     */
    private fun expiredSessionEngine() = MockEngine { request ->
        val path = request.url.encodedPath
        val authorization = request.headers[HttpHeaders.Authorization]
        when {
            path.endsWith("/protected") ->
                if (authorization == "Bearer access-token-abc") {
                    respond(content = "{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                } else {
                    respond(
                        content = "Unauthorized",
                        status = HttpStatusCode.Unauthorized,
                        headers = jsonHeaders,
                    )
                }

            path.endsWith("/auth/token/refresh") ->
                respond(
                    content = "Unauthorized",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )

            path.endsWith("/security/public-key-certificates") ->
                respond(
                    content =
                        """[{"certificate":"AAAA","validFrom":"2024-01-01T00:00:00Z","validTo":"2099-01-01T00:00:00Z","usage":["KsefTokenEncryption"]}]""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )

            path.endsWith("/auth/challenge") ->
                respond(
                    content =
                        """{"challenge":"abc123","timestamp":"2024-01-01T00:00:00Z","timestampMs":1704067200000,"clientIp":"127.0.0.1"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )

            path.endsWith("/auth/ksef-token") ->
                respond(
                    content =
                        """{"referenceNumber":"ref-123","authenticationToken":{"token":"auth-token-xyz","validUntil":"2024-01-01T01:00:00Z"}}""",
                    status = HttpStatusCode.Accepted,
                    headers = jsonHeaders,
                )

            path.endsWith("/auth/ref-123") ->
                respond(
                    content =
                        """{"startDate":"2024-01-01T00:00:00Z","authenticationMethodInfo":{"category":"Token","code":"Token","displayName":"Token"},"status":{"code":200,"description":"OK"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )

            path.endsWith("/auth/token/redeem") ->
                respond(
                    content =
                        """{"accessToken":{"token":"access-token-abc","validUntil":"2024-01-01T02:00:00Z"},"refreshToken":{"token":"refresh-token-def","validUntil":"2024-01-08T00:00:00Z"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )

            else ->
                respond(content = "{}", status = HttpStatusCode.NotFound, headers = jsonHeaders)
        }
    }

    private fun buildPlainClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(json, contentType = ContentType.Any) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }

    private fun buildTokenStore(): TokenStore {
        val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        val tmpPath = tmpDir / "test_prefs_${Random.nextInt()}.preferences_pb"
        val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tmpPath })
        return TokenStore(dataStore, InMemorySecureTokenStorage())
    }

    @Test
    fun expiredRefreshToken_withRememberedCredentials_reauthenticatesAndRetries() = runTest {
        val engine = expiredSessionEngine()
        val sessionHolder =
            SessionHolder().apply {
                update(accessToken = "expired-access", refreshToken = "expired-refresh")
            }
        val sessionEventBus = SessionEventBus()
        val tokenStore =
            buildTokenStore().apply {
                saveNip("1234567890")
                saveToken("remembered-ksef-token")
            }
        val authenticator = KsefAuthenticator(buildPlainClient(engine), FakeCrypto)
        val client =
            KsefApiClient.create(
                sessionHolder = sessionHolder,
                json = json,
                sessionEventBus = sessionEventBus,
                tokenStore = tokenStore,
                authenticator = authenticator,
                engine = engine,
            )

        val response = client.get("${sessionHolder.baseUrl}/protected")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("access-token-abc", sessionHolder.accessToken)
        assertEquals("refresh-token-def", sessionHolder.refreshToken)
        assertEquals("1234567890", sessionHolder.nip)
    }

    @Test
    fun expiredRefreshToken_withoutRememberedCredentials_clearsSessionAndNotifiesExpiry() =
        runTest {
            val engine = expiredSessionEngine()
            val sessionHolder =
                SessionHolder().apply {
                    update(accessToken = "expired-access", refreshToken = "expired-refresh")
                }
            val sessionEventBus = SessionEventBus()
            val tokenStore = buildTokenStore()
            val authenticator = KsefAuthenticator(buildPlainClient(engine), FakeCrypto)
            val client =
                KsefApiClient.create(
                    sessionHolder = sessionHolder,
                    json = json,
                    sessionEventBus = sessionEventBus,
                    tokenStore = tokenStore,
                    authenticator = authenticator,
                    engine = engine,
                )

            val result = runCatching { client.get("${sessionHolder.baseUrl}/protected") }

            assertTrue(result.isFailure)
            assertNull(sessionHolder.accessToken)
            assertEquals(Unit, sessionEventBus.sessionExpired.first())
        }

    private class InMemorySecureTokenStorage : SecureTokenStorage {
        private var token: String? = null

        override suspend fun saveToken(token: String) {
            this.token = token
        }

        override suspend fun readToken(): String? = token

        override suspend fun clearToken() {
            token = null
        }
    }
}
