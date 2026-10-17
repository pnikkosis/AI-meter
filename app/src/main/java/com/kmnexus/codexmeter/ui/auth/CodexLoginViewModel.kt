package com.kmnexus.codexmeter.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnect
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnector.ConnectResult
import com.kmnexus.codexmeter.providers.codex.auth.CodexBrowserConnector.ConnectStage
import com.kmnexus.codexmeter.providers.codex.auth.CodexOAuthConfig
import com.kmnexus.codexmeter.providers.common.auth.AuthorizationCallbackReceiver
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackPages
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackResult
import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CodexLoginPhase { Ready, WaitingForBrowser, Connecting, Connected, Failed }

enum class CodexLoginFailure(@get:StringRes val messageResId: Int) {
    BrowserUnavailable(R.string.codex_login_error_browser_unavailable),
    LoopbackUnavailable(R.string.codex_login_error_loopback_unavailable),
    Denied(R.string.claude_login_error_denied),
    NoCodexAccess(R.string.codex_login_error_no_codex_access),
    ProviderRedirectError(R.string.codex_login_error_redirect),
    TimedOut(R.string.claude_login_error_timed_out),
    ExchangeRejected(R.string.codex_login_error_exchange_rejected),
    UsageNotAllowed(R.string.codex_login_error_usage_not_allowed),
    Network(R.string.error_network),
    RateLimited(R.string.error_rate_limited),
    ProviderUnavailable(R.string.error_provider_unavailable),
    ResponseInvalid(R.string.error_response_invalid),
    AccountMismatch(R.string.codex_login_error_account_mismatch),
    StorageFailed(R.string.error_session_persistence),
}

data class CodexLoginUiState(
    val phase: CodexLoginPhase = CodexLoginPhase.Ready,
    val failure: CodexLoginFailure? = null,
    val connectedAccountName: String? = null,
    /** Email of the other account signed in to during a re-login, when OpenAI reported it. */
    val mismatchEmail: String? = null,
    val isRelogin: Boolean = false,
)

/**
 * Drives the Codex browser sign-in, as `codex login` does it: PKCE + state, a loopback receiver on
 * the ports Codex CLI registers, opening OpenAI's page in the browser, waiting for the redirect and
 * storing the validated session. Only one attempt runs at a time; leaving the screen cancels it and
 * closes the loopback port.
 */
class CodexLoginViewModel(
    private val connector: CodexBrowserConnect,
    private val expectedProviderAccountId: ProviderAccountId? = null,
    private val pkceFactory: () -> PkceSession = { PkceSession.create() },
    private val receiverFactory: (state: String, pages: CallbackPages) -> AuthorizationCallbackReceiver =
        { state, pages ->
            LoopbackAuthorizationReceiver.start(
                expectedState = state,
                pages = pages,
                callbackPath = CodexOAuthConfig.BROWSER_CALLBACK_PATH,
                ports = CodexOAuthConfig.BROWSER_CALLBACK_PORTS,
            )
        },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = SIGN_IN_TIMEOUT_MILLIS,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CodexLoginUiState(isRelogin = expectedProviderAccountId != null))
    val uiState: StateFlow<CodexLoginUiState> = _uiState.asStateFlow()

    private val browserRequestChannel = Channel<String>(Channel.BUFFERED)

    /** Authorization URLs the screen must open in the browser (one event per request). */
    val browserRequests: Flow<String> = browserRequestChannel.receiveAsFlow()

    private var attempt: Job? = null
    private var currentUrl: String? = null

    // Android cuts the network of an app whose screen stays behind another one (here the browser
    // tab) after a few seconds, so provider requests wait until the screen is in front again.
    private val screenVisible = MutableStateFlow(true)

    fun start(pages: CallbackPages) {
        if (attempt?.isActive == true) return
        attempt = viewModelScope.launch {
            update { it.copy(phase = CodexLoginPhase.WaitingForBrowser, failure = null, mismatchEmail = null) }
            val pkce = pkceFactory()
            val receiver = try {
                withContext(ioDispatcher) { receiverFactory(pkce.state, pages) }
            } catch (_: Exception) {
                fail(CodexLoginFailure.LoopbackUnavailable)
                return@launch
            }
            receiver.use {
                val redirectUri = CodexOAuthConfig.browserRedirectUri(receiver.port)
                val url = CodexOAuthConfig.browserAuthorizationUrl(pkce, redirectUri)
                currentUrl = url
                browserRequestChannel.send(url)
                val callback = receiver.awaitResult(timeoutMillis)
                currentUrl = null
                handleCallback(callback, pkce, redirectUri)
            }
        }
    }

    /** Re-opens the same authorization page (for example after the user closed the tab too early). */
    fun reopenBrowser() {
        val url = currentUrl ?: return
        if (_uiState.value.phase == CodexLoginPhase.WaitingForBrowser) {
            browserRequestChannel.trySend(url)
        }
    }

    /** The screen reports when it is shown or hidden (for example behind the browser tab). */
    fun onScreenVisibilityChanged(visible: Boolean) {
        screenVisible.value = visible
    }

    /** Called when no app can open the sign-in page. */
    fun onBrowserUnavailable() {
        attempt?.cancel()
        currentUrl = null
        fail(CodexLoginFailure.BrowserUnavailable)
    }

    fun cancel() {
        attempt?.cancel()
        currentUrl = null
        if (_uiState.value.phase != CodexLoginPhase.Connected) {
            update { it.copy(phase = CodexLoginPhase.Ready, failure = null) }
        }
    }

    override fun onCleared() {
        attempt?.cancel()
        super.onCleared()
    }

    private suspend fun handleCallback(callback: CallbackResult, pkce: PkceSession, redirectUri: String) {
        when (callback) {
            CallbackResult.TimedOut -> fail(CodexLoginFailure.TimedOut)
            is CallbackResult.ProviderError -> fail(callback.toFailure())
            is CallbackResult.Code -> {
                when (val result = connectInForeground(callback.code, pkce, redirectUri)) {
                    is ConnectResult.Connected -> update {
                        it.copy(
                            phase = CodexLoginPhase.Connected,
                            failure = null,
                            connectedAccountName = result.account.displayName,
                        )
                    }
                    is ConnectResult.AccountMismatch -> {
                        update { it.copy(mismatchEmail = result.signedInEmail) }
                        fail(CodexLoginFailure.AccountMismatch)
                    }
                    is ConnectResult.Failed -> fail(result.toFailure())
                }
            }
        }
    }

    private suspend fun connectInForeground(code: String, pkce: PkceSession, redirectUri: String): ConnectResult {
        screenVisible.first { it }
        update { it.copy(phase = CodexLoginPhase.Connecting) }
        val result = connector.connect(code, pkce, redirectUri, expectedProviderAccountId)
        if (result !is ConnectResult.Failed || result.stage != ConnectStage.Exchange || result.error !is QuotaError.Network) {
            return result
        }
        // Back in front, Android returns the network a moment after the screen; a code the server
        // never received can be sent once more (if it did arrive, OpenAI refuses the repeat).
        delay(NETWORK_RETRY_DELAY_MILLIS)
        screenVisible.first { it }
        return connector.connect(code, pkce, redirectUri, expectedProviderAccountId)
    }

    private fun CallbackResult.ProviderError.toFailure(): CodexLoginFailure = when {
        // OpenAI reports a ChatGPT account without Codex as access_denied and names the cause only
        // in the description (Codex CLI tells them apart the same way).
        error == "access_denied" && description?.contains("missing_codex_entitlement", ignoreCase = true) == true ->
            CodexLoginFailure.NoCodexAccess
        error == "access_denied" -> CodexLoginFailure.Denied
        else -> CodexLoginFailure.ProviderRedirectError
    }

    private fun ConnectResult.Failed.toFailure(): CodexLoginFailure = when (error) {
        null -> CodexLoginFailure.StorageFailed
        is QuotaError.AuthRequired ->
            if (stage == ConnectStage.Exchange) CodexLoginFailure.ExchangeRejected else CodexLoginFailure.UsageNotAllowed
        is QuotaError.Network -> CodexLoginFailure.Network
        is QuotaError.RateLimited -> CodexLoginFailure.RateLimited
        is QuotaError.ProviderUnavailable -> CodexLoginFailure.ProviderUnavailable
        is QuotaError.ResponseInvalid -> CodexLoginFailure.ResponseInvalid
    }

    private fun fail(failure: CodexLoginFailure) {
        update { it.copy(phase = CodexLoginPhase.Failed, failure = failure) }
    }

    private fun update(transform: (CodexLoginUiState) -> CodexLoginUiState) {
        _uiState.value = transform(_uiState.value)
    }

    companion object {
        /** Generous enough for a password manager, 2FA and a slow network. */
        const val SIGN_IN_TIMEOUT_MILLIS = 10 * 60 * 1000L

        /** Pause before sending again an exchange that found no network. */
        const val NETWORK_RETRY_DELAY_MILLIS = 1_000L

        fun factory(
            connector: CodexBrowserConnect,
            expectedProviderAccountId: ProviderAccountId?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CodexLoginViewModel(
                        connector = connector,
                        expectedProviderAccountId = expectedProviderAccountId,
                    ) as T
            }
    }
}
