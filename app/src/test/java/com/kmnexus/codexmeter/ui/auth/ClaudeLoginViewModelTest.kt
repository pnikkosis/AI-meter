package com.kmnexus.codexmeter.ui.auth

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector.ConnectResult
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector.ConnectStage
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeConnect
import com.kmnexus.codexmeter.providers.common.auth.AuthorizationCallbackReceiver
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackPages
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackResult
import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClaudeLoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pages = CallbackPages.simple("ok", "ok", "fail", "fail")
    private val pkce = PkceSession.create()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful sign-in opens the browser on the loopback redirect and connects the account`() = runTest(dispatcher) {
        val receiver = FakeReceiver(port = 40111)
        val connector = RecordingConnector(ConnectResult.Connected(account("person@example.com")))
        val viewModel = viewModel(connector, receiver)

        viewModel.start(pages)
        runCurrent()

        assertEquals(ClaudeLoginPhase.WaitingForBrowser, viewModel.uiState.value.phase)
        val url = viewModel.browserRequests.first()
        val params = query(url)
        assertEquals("http://localhost:40111/callback", params["redirect_uri"])
        assertEquals(pkce.state, params["state"])
        assertEquals(pkce.codeChallenge, params["code_challenge"])
        assertSame(pkce.state, receiver.expectedState)

        receiver.result.complete(CallbackResult.Code("auth-code"))
        runCurrent()

        assertEquals(ClaudeLoginPhase.Connected, viewModel.uiState.value.phase)
        assertEquals("person@example.com", viewModel.uiState.value.connectedAccountName)
        val call = connector.calls.single()
        assertEquals("auth-code", call.code)
        assertSame(pkce, call.pkce)
        assertEquals("http://localhost:40111/callback", call.redirectUri)
        assertNull(call.expected)
        assertTrue(receiver.closed)
    }

    @Test
    fun `browser errors and timeouts map to their own messages`() = runTest(dispatcher) {
        val cases = mapOf(
            CallbackResult.ProviderError("access_denied") to ClaudeLoginFailure.Denied,
            CallbackResult.ProviderError("invalid_scope") to ClaudeLoginFailure.ScopeRejected,
            CallbackResult.ProviderError("server_error") to ClaudeLoginFailure.ProviderRedirectError,
            CallbackResult.TimedOut to ClaudeLoginFailure.TimedOut,
        )
        cases.forEach { (callback, expected) ->
            val receiver = FakeReceiver()
            val connector = RecordingConnector(ConnectResult.Connected(account("x")))
            val viewModel = viewModel(connector, receiver)

            viewModel.start(pages)
            runCurrent()
            receiver.result.complete(callback)
            runCurrent()

            assertEquals(ClaudeLoginPhase.Failed, viewModel.uiState.value.phase)
            assertEquals(expected, viewModel.uiState.value.failure)
            assertTrue("connector must not run for $callback", connector.calls.isEmpty())
        }
    }

    @Test
    fun `connector failures keep exchange, validation and transport problems apart`() = runTest(dispatcher) {
        val cases = listOf(
            ConnectResult.Failed(QuotaError.AuthRequired(400, "d"), ConnectStage.Exchange) to ClaudeLoginFailure.ExchangeRejected,
            ConnectResult.Failed(QuotaError.AuthRequired(403, "d"), ConnectStage.Validate) to ClaudeLoginFailure.UsageNotAllowed,
            ConnectResult.Failed(QuotaError.Network("d"), ConnectStage.Exchange) to ClaudeLoginFailure.Network,
            ConnectResult.Failed(QuotaError.RateLimited("d"), ConnectStage.Validate) to ClaudeLoginFailure.RateLimited,
            ConnectResult.Failed(QuotaError.ProviderUnavailable(502, "d"), ConnectStage.Validate) to ClaudeLoginFailure.ProviderUnavailable,
            ConnectResult.Failed(QuotaError.ResponseInvalid("d"), ConnectStage.Validate) to ClaudeLoginFailure.ResponseInvalid,
            ConnectResult.Failed(null, ConnectStage.Persist) to ClaudeLoginFailure.StorageFailed,
            ConnectResult.AccountMismatch("other@example.com") to ClaudeLoginFailure.AccountMismatch,
        )
        cases.forEach { (connectResult, expected) ->
            val receiver = FakeReceiver()
            val viewModel = viewModel(RecordingConnector(connectResult), receiver)

            viewModel.start(pages)
            runCurrent()
            receiver.result.complete(CallbackResult.Code("code"))
            runCurrent()

            assertEquals(expected, viewModel.uiState.value.failure)
        }
    }

    @Test
    fun `re-login passes the expected account and reports the other email on mismatch`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val connector = RecordingConnector(ConnectResult.AccountMismatch("other@example.com"))
        val viewModel = viewModel(connector, receiver, expected = ProviderAccountId("account-uuid-1"))

        assertTrue(viewModel.uiState.value.isRelogin)
        viewModel.start(pages)
        runCurrent()
        receiver.result.complete(CallbackResult.Code("code"))
        runCurrent()

        assertEquals(ProviderAccountId("account-uuid-1"), connector.calls.single().expected)
        assertEquals(ClaudeLoginFailure.AccountMismatch, viewModel.uiState.value.failure)
        assertEquals("other@example.com", viewModel.uiState.value.mismatchEmail)
    }

    @Test
    fun `cancel closes the loopback receiver and returns to the start`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val connector = RecordingConnector(ConnectResult.Connected(account("x")))
        val viewModel = viewModel(connector, receiver)

        viewModel.start(pages)
        runCurrent()
        viewModel.cancel()
        runCurrent()

        assertEquals(ClaudeLoginPhase.Ready, viewModel.uiState.value.phase)
        assertTrue(receiver.closed)
        assertTrue(connector.calls.isEmpty())
    }

    @Test
    fun `missing browser and unavailable loopback port are reported`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val viewModel = viewModel(RecordingConnector(ConnectResult.Connected(account("x"))), receiver)
        viewModel.start(pages)
        runCurrent()
        viewModel.onBrowserUnavailable()
        runCurrent()
        assertEquals(ClaudeLoginFailure.BrowserUnavailable, viewModel.uiState.value.failure)
        assertTrue(receiver.closed)

        val noPort = ClaudeLoginViewModel(
            connector = RecordingConnector(ConnectResult.Connected(account("x"))),
            pkceFactory = { pkce },
            receiverFactory = { _, _ -> throw IOException("no port") },
            ioDispatcher = dispatcher,
        )
        noPort.start(pages)
        runCurrent()
        assertEquals(ClaudeLoginFailure.LoopbackUnavailable, noPort.uiState.value.failure)
    }

    @Test
    fun `a second start while waiting does not open another receiver`() = runTest(dispatcher) {
        var created = 0
        val receiver = FakeReceiver()
        val viewModel = ClaudeLoginViewModel(
            connector = RecordingConnector(ConnectResult.Connected(account("x"))),
            pkceFactory = { pkce },
            receiverFactory = { state, _ ->
                created++
                receiver.also { it.expectedState = state }
            },
            ioDispatcher = dispatcher,
        )

        viewModel.start(pages)
        runCurrent()
        viewModel.start(pages)
        runCurrent()

        assertEquals(1, created)
    }

    private fun viewModel(
        connector: ClaudeConnect,
        receiver: FakeReceiver,
        expected: ProviderAccountId? = null,
    ) = ClaudeLoginViewModel(
        connector = connector,
        expectedProviderAccountId = expected,
        pkceFactory = { pkce },
        receiverFactory = { state, _ -> receiver.also { it.expectedState = state } },
        ioDispatcher = dispatcher,
    )

    private fun account(name: String) = ProviderAccount.createNew(
        localAccountId = LocalAccountId("claude-1"),
        providerId = ProviderId("claude"),
        providerAccountId = ProviderAccountId("account-uuid-1"),
        displayName = name,
        now = Instant.parse("2026-10-17T12:00:00Z"),
    )

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val (name, value) = it.split('=', limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

    private class FakeReceiver(override val port: Int = 40000) : AuthorizationCallbackReceiver {
        val result = CompletableDeferred<CallbackResult>()
        var expectedState: String? = null
        var closed = false

        override suspend fun awaitResult(timeoutMillis: Long): CallbackResult = try {
            result.await()
        } finally {
            closed = true
        }

        override fun close() {
            closed = true
        }
    }

    private class RecordingConnector(private val result: ConnectResult) : ClaudeConnect {
        data class Call(val code: String, val pkce: PkceSession, val redirectUri: String, val expected: ProviderAccountId?)

        val calls = mutableListOf<Call>()

        override suspend fun connect(
            code: String,
            pkce: PkceSession,
            redirectUri: String,
            expectedProviderAccountId: ProviderAccountId?,
        ): ConnectResult {
            calls += Call(code, pkce, redirectUri, expectedProviderAccountId)
            return result
        }
    }
}
