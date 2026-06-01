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

package com.kgurgul.openksef.ui.login

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kgurgul.openksef.common.UiText
import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.local.SecureTokenStorage
import com.kgurgul.openksef.data.local.TokenStore
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.repository.KsefRepository
import com.kgurgul.openksef.domain.model.KsefEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okio.FileSystem
import openksef.shared.generated.resources.Res
import openksef.shared.generated.resources.error_nip_invalid
import openksef.shared.generated.resources.error_token_required

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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
    fun initialState_hasDefaultValues() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(KsefEnvironment.TEST, state.environment)
        assertFalse(state.isLoading)
        assertFalse(state.isLoggedIn)
    }

    @Test
    fun onNipChanged_updatesState() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNipChanged("1234567890")

        assertEquals("1234567890", viewModel.uiState.value.nip)
    }

    @Test
    fun login_invalidNip_setsError() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNipChanged("123")
        viewModel.onTokenChanged("token")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiText.Resource(Res.string.error_nip_invalid), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun login_emptyToken_setsError() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNipChanged("1234567890")
        viewModel.onTokenChanged("")
        viewModel.login()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            UiText.Resource(Res.string.error_token_required),
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun onEnvironmentChanged_updatesState() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEnvironmentChanged(KsefEnvironment.PRODUCTION)

        assertEquals(KsefEnvironment.PRODUCTION, viewModel.uiState.value.environment)
    }

    private fun createViewModel(): LoginViewModel {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/auth/challenge") ->
                    respond(
                        content =
                            """{"challenge":"test-challenge","timestamp":"2024-01-01T00:00:00Z","timestampMs":1704067200000,"clientIp":"127.0.0.1"}""",
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )

                path.endsWith("/auth/ksef-token") ->
                    respond(
                        content =
                            """{"referenceNumber":"ref-123","authenticationToken":{"token":"auth-token","validUntil":"2024-01-01T01:00:00Z"}}""",
                        status = HttpStatusCode.Accepted,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )

                path.endsWith("/auth/ref-123") ->
                    respond(
                        content =
                            """{"startDate":"2024-01-01T00:00:00Z","authenticationMethodInfo":{"category":"Token","code":"Token","displayName":"Token"},"status":{"code":200,"description":"OK"}}""",
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )

                path.endsWith("/auth/token/redeem") ->
                    respond(
                        content =
                            """{"accessToken":{"token":"access-token","validUntil":"2024-01-01T02:00:00Z"},"refreshToken":{"token":"refresh-token","validUntil":"2024-01-08T00:00:00Z"}}""",
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )

                else ->
                    respond(
                        content = "{}",
                        status = HttpStatusCode.NotFound,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
            }
        }
        val mockClient =
            HttpClient(engine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                            encodeDefaults = true
                        }
                    )
                }
                defaultRequest { contentType(ContentType.Application.Json) }
                expectSuccess = false
            }

        val sessionHolder = SessionHolder()
        val api = KsefApi(mockClient)
        val crypto =
            object : KsefCrypto {
                override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data
                override fun secureRandomBytes(size: Int) = ByteArray(size)
                override fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray) = data
            }
        val repository = KsefRepository(api, sessionHolder, crypto)

        val tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
        val tmpPath = tmpDir / "test_prefs_${Random.nextInt()}.preferences_pb"
        val dataStore = PreferenceDataStoreFactory.createWithPath(produceFile = { tmpPath })
        val tokenStore = TokenStore(dataStore, InMemorySecureTokenStorage())

        return LoginViewModel(repository, tokenStore)
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
