package com.kgurgul.openksef.ui.invoicedetail

import com.kgurgul.openksef.data.SessionHolder
import com.kgurgul.openksef.data.remote.KsefApi
import com.kgurgul.openksef.data.remote.KsefCrypto
import com.kgurgul.openksef.data.repository.KsefRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvoiceDetailViewModelTest {

    @Test
    fun initialState_carriesKsefReferenceNumber() = runTest {
        val viewModel = createViewModel(
            ksefRef = "KSEF-ABC-001",
            responseStatus = HttpStatusCode.OK,
            responseBody = "<Faktura/>",
        )

        val state = viewModel.uiState.first { !it.isLoading }

        assertEquals("KSEF-ABC-001", state.ksefReferenceNumber)
    }

    @Test
    fun loadOnSubscribe_success_emitsInvoiceXml() = runTest {
        val xml = "<Faktura><Naglowek/></Faktura>"
        val viewModel = createViewModel(
            ksefRef = "KSEF-XYZ-007",
            responseStatus = HttpStatusCode.OK,
            responseBody = xml,
        )

        val state = viewModel.uiState.first { it.invoiceXml != null }

        assertEquals(xml, state.invoiceXml)
        assertEquals(false, state.isLoading)
        assertEquals("KSEF-XYZ-007", state.ksefReferenceNumber)
    }

    @Test
    fun loadOnSubscribe_failure_emitsErrorEvent_andClearsLoading() = runTest {
        val viewModel = createViewModel(
            ksefRef = "KSEF-FAIL-001",
            responseStatus = HttpStatusCode.InternalServerError,
            responseBody = "boom",
        )

        // Keep uiState collected so the load coroutine actually runs.
        backgroundScope.launch { viewModel.uiState.collect {} }

        val event = viewModel.events.first()
        assertEquals(true, event is InvoiceDetailEvent.ShowError)

        val state = viewModel.uiState.first { !it.isLoading && it.ksefReferenceNumber.isNotEmpty() }
        assertNull(state.invoiceXml)
        assertEquals("KSEF-FAIL-001", state.ksefReferenceNumber)
    }

    private fun createViewModel(
        ksefRef: String,
        responseStatus: HttpStatusCode,
        responseBody: String,
    ): InvoiceDetailViewModel {
        val engine = MockEngine { _ ->
            respond(
                content = responseBody,
                status = responseStatus,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Xml.toString(),
                ),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest { contentType(ContentType.Application.Json) }
            expectSuccess = true
        }
        val sessionHolder = SessionHolder().apply { accessToken = "test-token" }
        val api = KsefApi(client)
        val crypto = object : KsefCrypto {
            override fun rsaOaepSha256Encrypt(data: ByteArray, certificateDer: ByteArray) = data
        }
        val repository = KsefRepository(api, sessionHolder, crypto)
        return InvoiceDetailViewModel(ksefRef, repository)
    }
}
