package com.kmnexus.codexmeter.ui.auth

import com.kmnexus.codexmeter.domain.model.LocalAccountId
import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.model.ProviderId
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnect
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnector.ConnectResult
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnector.ConnectStage
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
class CodexLoginViewModelTest {
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
    fun `successful sign-in opens OpenAI on the codex loopback redirect and connects the account`() = runTest(dispatcher) {
        val receiver = FakeReceiver(port = 1455)
        val connector = RecordingConnector(ConnectResult.Connected(account("person@example.com")))
        val viewModel = viewModel(connector, receiver)

        viewModel.start(pages)
        runCurrent()

        assertEquals(CodexLoginPhase.WaitingForBrowser, viewModel.uiState.value.phase)
        val url = viewModel.browserRequests.first()
        assertTrue(url.startsWith("https://auth.openai.com/oauth/authorize?"))
        val params = query(url)
        assertEquals("http://127.0.0.1:1455/auth/callback", params["redirect_uri"])
        assertEquals(pkce.state, params["state"])
        assertEquals(pkce.codeChallenge, params["code_challenge"])
        assertSame(pkce.state, receiver.expectedState)

        receiver.result.complete(CallbackResult.Code("auth-code"))
        runCurrent()

        assertEquals(CodexLoginPhase.Connected, viewModel.uiState.value.phase)
        assertEquals("person@example.com", viewModel.uiState.value.connectedAccountName)
        val call = connector.calls.single()
        assertEquals("auth-code", call.code)
        assertSame(pkce, call.pkce)
        assertEquals("http://127.0.0.1:1455/auth/callback", call.redirectUri)
        assertNull(call.expected)
        assertTrue(receiver.closed)
    }

    @Test
    fun `browser errors and timeouts map to their own messages`() = runTest(dispatcher) {
        val cases = mapOf(
            CallbackResult.ProviderError("access_denied") to CodexLoginFailure.Denied,
            // OpenAI reports an account without Codex as access_denied and names the cause in the description.
            CallbackResult.ProviderError("access_denied", description = "missing_codex_entitlement") to
                CodexLoginFailure.NoCodexAccess,
            CallbackResult.ProviderError("access_denied", description = "User cancelled") to CodexLoginFailure.Denied,
            CallbackResult.ProviderError("server_error") to CodexLoginFailure.ProviderRedirectError,
            CallbackResult.TimedOut to CodexLoginFailure.TimedOut,
        )
        cases.forEach { (callback, expected) ->
            val receiver = FakeReceiver()
            val connector = RecordingConnector(ConnectResult.Connected(account("x")))
            val viewModel = viewModel(connector, receiver)

            viewModel.start(pages)
            runCurrent()
            receiver.result.complete(callback)
            runCurrent()

            assertEquals(CodexLoginPhase.Failed, viewModel.uiState.value.phase)
            assertEquals(expected, viewModel.uiState.value.failure)
            assertTrue("connector must not run for $callback", connector.calls.isEmpty())
        }
    }

    @Test
    fun `connector failures keep exchange, validation and transport problems apart`() = runTest(dispatcher) {
        val cases = listOf(
            ConnectResult.Failed(QuotaError.AuthRequired(400, "d"), ConnectStage.Exchange) to CodexLoginFailure.ExchangeRejected,
            ConnectResult.Failed(QuotaError.AuthRequired(403, "d"), ConnectStage.Validate) to CodexLoginFailure.UsageNotAllowed,
            ConnectResult.Failed(QuotaError.Network("d"), ConnectStage.Exchange) to CodexLoginFailure.Network,
            ConnectResult.Failed(QuotaError.RateLimited("d"), ConnectStage.Validate) to CodexLoginFailure.RateLimited,
            ConnectResult.Failed(QuotaError.ProviderUnavailable(502, "d"), ConnectStage.Validate) to CodexLoginFailure.ProviderUnavailable,
            ConnectResult.Failed(QuotaError.ResponseInvalid("d"), ConnectStage.Validate) to CodexLoginFailure.ResponseInvalid,
            ConnectResult.Failed(null, ConnectStage.Persist) to CodexLoginFailure.StorageFailed,
            ConnectResult.AccountMismatch("other@example.com") to CodexLoginFailure.AccountMismatch,
        )
        cases.forEach { (connectResult, expected) ->
            val receiver = FakeReceiver()
            val viewModel = viewModel(RecordingConnector(connectResult), receiver)

            viewModel.start(pages)
            runCurrent()
            receiver.result.complete(CallbackResult.Code("code"))
            advanceUntilIdle()

            assertEquals(expected, viewModel.uiState.value.failure)
        }
    }

    @Test
    fun `re-login passes the expected account and reports the other email on mismatch`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val connector = RecordingConnector(ConnectResult.AccountMismatch("other@example.com"))
        val viewModel = viewModel(connector, receiver, expected = ProviderAccountId("chatgpt-account-1"))

        assertTrue(viewModel.uiState.value.isRelogin)
        viewModel.start(pages)
        runCurrent()
        receiver.result.complete(CallbackResult.Code("code"))
        runCurrent()

        assertEquals(ProviderAccountId("chatgpt-account-1"), connector.calls.single().expected)
        assertEquals(CodexLoginFailure.AccountMismatch, viewModel.uiState.value.failure)
        assertEquals("other@example.com", viewModel.uiState.value.mismatchEmail)
    }

    @Test
    fun `the code is exchanged only once the app is back in front of the browser`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val connector = RecordingConnector(ConnectResult.Connected(account("person@example.com")))
        val viewModel = viewModel(connector, receiver)

        viewModel.start(pages)
        runCurrent()
        viewModel.onScreenVisibilityChanged(visible = false)
        receiver.result.complete(CallbackResult.Code("auth-code"))
        advanceUntilIdle()

        assertTrue("no request while the browser covers the app", connector.calls.isEmpty())
        assertEquals(CodexLoginPhase.WaitingForBrowser, viewModel.uiState.value.phase)

        viewModel.onScreenVisibilityChanged(visible = true)
        runCurrent()

        assertEquals("auth-code", connector.calls.single().code)
        assertEquals(CodexLoginPhase.Connected, viewModel.uiState.value.phase)
    }

    @Test
    fun `an exchange that found no network is sent once more after a short pause`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val connector = RecordingConnector(
            ConnectResult.Failed(QuotaError.Network("d"), ConnectStage.Exchange),
            ConnectResult.Connected(account("person@example.com")),
        )
        val viewModel = viewModel(connector, receiver)

        viewModel.start(pages)
        runCurrent()
        receiver.result.complete(CallbackResult.Code("auth-code"))
        runCurrent()

        assertEquals(1, connector.calls.size)
        advanceTimeBy(CodexLoginViewModel.NETWORK_RETRY_DELAY_MILLIS + 1)

        assertEquals(listOf("auth-code", "auth-code"), connector.calls.map { it.code })
        assertEquals(CodexLoginPhase.Connected, viewModel.uiState.value.phase)
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

        assertEquals(CodexLoginPhase.Ready, viewModel.uiState.value.phase)
        assertTrue(receiver.closed)
        assertTrue(connector.calls.isEmpty())
    }

    @Test
    fun `missing browser and busy loopback ports are reported`() = runTest(dispatcher) {
        val receiver = FakeReceiver()
        val viewModel = viewModel(RecordingConnector(ConnectResult.Connected(account("x"))), receiver)
        viewModel.start(pages)
        runCurrent()
        viewModel.onBrowserUnavailable()
        runCurrent()
        assertEquals(CodexLoginFailure.BrowserUnavailable, viewModel.uiState.value.failure)
        assertTrue(receiver.closed)

        val noPort = CodexLoginViewModel(
            connector = RecordingConnector(ConnectResult.Connected(account("x"))),
            pkceFactory = { pkce },
            receiverFactory = { _, _ -> throw IOException("ports in use") },
            ioDispatcher = dispatcher,
        )
        noPort.start(pages)
        runCurrent()
        assertEquals(CodexLoginFailure.LoopbackUnavailable, noPort.uiState.value.failure)
    }

    private fun viewModel(
        connector: CodexBrowserConnect,
        receiver: FakeReceiver,
        expected: ProviderAccountId? = null,
    ) = CodexLoginViewModel(
        connector = connector,
        expectedProviderAccountId = expected,
        pkceFactory = { pkce },
        receiverFactory = { state, _ -> receiver.also { it.expectedState = state } },
        ioDispatcher = dispatcher,
    )

    private fun account(name: String) = ProviderAccount.createNew(
        localAccountId = LocalAccountId("codex-1"),
        providerId = ProviderId("codex"),
        providerAccountId = ProviderAccountId("chatgpt-account-1"),
        displayName = name,
        now = Instant.parse("2026-10-17T12:00:00Z"),
    )

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val (name, value) = it.split('=', limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

    private class FakeReceiver(override val port: Int = 1455) : AuthorizationCallbackReceiver {
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

    /** Answers with [results] in order, repeating the last one. */
    private class RecordingConnector(private vararg val results: ConnectResult) : CodexBrowserConnect {
        data class Call(val code: String, val pkce: PkceSession, val redirectUri: String, val expected: ProviderAccountId?)

        val calls = mutableListOf<Call>()

        override suspend fun connect(
            code: String,
            pkce: PkceSession,
            redirectUri: String,
            expectedProviderAccountId: ProviderAccountId?,
        ): ConnectResult {
            calls += Call(code, pkce, redirectUri, expectedProviderAccountId)
            return results[minOf(calls.size, results.size) - 1]
        }
    }
}
