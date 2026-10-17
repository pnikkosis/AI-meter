package com.kmnexus.codexmeter.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector.ConnectResult
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeAccountConnector.ConnectStage
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeConnect
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeOAuthConfig
import com.kmnexus.codexmeter.providers.common.auth.AuthorizationCallbackReceiver
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackPages
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackResult
import com.kmnexus.codexmeter.providers.common.auth.PkceSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClaudeLoginPhase { Ready, WaitingForBrowser, Connecting, Connected, Failed }

enum class ClaudeLoginFailure(@get:StringRes val messageResId: Int) {
    BrowserUnavailable(R.string.claude_login_error_browser_unavailable),
    LoopbackUnavailable(R.string.claude_login_error_loopback_unavailable),
    Denied(R.string.claude_login_error_denied),
    ScopeRejected(R.string.claude_login_error_scope_rejected),
    ProviderRedirectError(R.string.claude_login_error_redirect),
    TimedOut(R.string.claude_login_error_timed_out),
    ExchangeRejected(R.string.claude_login_error_exchange_rejected),
    UsageNotAllowed(R.string.claude_login_error_usage_not_allowed),
    Network(R.string.error_network),
    RateLimited(R.string.error_rate_limited),
    ProviderUnavailable(R.string.error_provider_unavailable),
    ResponseInvalid(R.string.error_response_invalid),
    AccountMismatch(R.string.claude_login_error_account_mismatch),
    StorageFailed(R.string.error_session_persistence),
}

data class ClaudeLoginUiState(
    val phase: ClaudeLoginPhase = ClaudeLoginPhase.Ready,
    val failure: ClaudeLoginFailure? = null,
    val connectedAccountName: String? = null,
    /** Email of the other account signed in to during a re-login, when the provider reported it. */
    val mismatchEmail: String? = null,
    val isRelogin: Boolean = false,
)

/**
 * Drives the Claude browser sign-in: PKCE + state, loopback receiver, opening the browser, waiting
 * for the redirect and storing the validated session. Only one attempt runs at a time; leaving the
 * screen cancels it and closes the loopback port.
 */
class ClaudeLoginViewModel(
    private val connector: ClaudeConnect,
    private val expectedProviderAccountId: ProviderAccountId? = null,
    private val pkceFactory: () -> PkceSession = { PkceSession.create() },
    private val receiverFactory: (state: String, pages: CallbackPages) -> AuthorizationCallbackReceiver =
        { state, pages -> LoopbackAuthorizationReceiver.start(expectedState = state, pages = pages) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = SIGN_IN_TIMEOUT_MILLIS,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClaudeLoginUiState(isRelogin = expectedProviderAccountId != null))
    val uiState: StateFlow<ClaudeLoginUiState> = _uiState.asStateFlow()

    private val browserRequestChannel = Channel<String>(Channel.BUFFERED)

    /** Authorization URLs the screen must open in the browser (one event per request). */
    val browserRequests: Flow<String> = browserRequestChannel.receiveAsFlow()

    private var attempt: Job? = null
    private var currentUrl: String? = null

    fun start(pages: CallbackPages) {
        if (attempt?.isActive == true) return
        attempt = viewModelScope.launch {
            update { it.copy(phase = ClaudeLoginPhase.WaitingForBrowser, failure = null, mismatchEmail = null) }
            val pkce = pkceFactory()
            val receiver = try {
                withContext(ioDispatcher) { receiverFactory(pkce.state, pages) }
            } catch (_: Exception) {
                fail(ClaudeLoginFailure.LoopbackUnavailable)
                return@launch
            }
            receiver.use {
                val redirectUri = ClaudeOAuthConfig.redirectUri(receiver.port)
                val url = ClaudeOAuthConfig.authorizationUrl(pkce, redirectUri)
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
        if (_uiState.value.phase == ClaudeLoginPhase.WaitingForBrowser) {
            browserRequestChannel.trySend(url)
        }
    }

    /** Called when no app can open the sign-in page. */
    fun onBrowserUnavailable() {
        attempt?.cancel()
        currentUrl = null
        fail(ClaudeLoginFailure.BrowserUnavailable)
    }

    fun cancel() {
        attempt?.cancel()
        currentUrl = null
        if (_uiState.value.phase != ClaudeLoginPhase.Connected) {
            update { it.copy(phase = ClaudeLoginPhase.Ready, failure = null) }
        }
    }

    override fun onCleared() {
        attempt?.cancel()
        super.onCleared()
    }

    private suspend fun handleCallback(callback: CallbackResult, pkce: PkceSession, redirectUri: String) {
        when (callback) {
            CallbackResult.TimedOut -> fail(ClaudeLoginFailure.TimedOut)
            is CallbackResult.ProviderError -> fail(
                when (callback.error) {
                    "access_denied" -> ClaudeLoginFailure.Denied
                    "invalid_scope" -> ClaudeLoginFailure.ScopeRejected
                    else -> ClaudeLoginFailure.ProviderRedirectError
                },
            )
            is CallbackResult.Code -> {
                update { it.copy(phase = ClaudeLoginPhase.Connecting) }
                when (val result = connector.connect(callback.code, pkce, redirectUri, expectedProviderAccountId)) {
                    is ConnectResult.Connected -> update {
                        it.copy(
                            phase = ClaudeLoginPhase.Connected,
                            failure = null,
                            connectedAccountName = result.account.displayName,
                        )
                    }
                    is ConnectResult.AccountMismatch -> {
                        update { it.copy(mismatchEmail = result.signedInEmail) }
                        fail(ClaudeLoginFailure.AccountMismatch)
                    }
                    is ConnectResult.Failed -> fail(result.toFailure())
                }
            }
        }
    }

    private fun ConnectResult.Failed.toFailure(): ClaudeLoginFailure = when (error) {
        null -> ClaudeLoginFailure.StorageFailed
        is QuotaError.AuthRequired ->
            if (stage == ConnectStage.Exchange) ClaudeLoginFailure.ExchangeRejected else ClaudeLoginFailure.UsageNotAllowed
        is QuotaError.Network -> ClaudeLoginFailure.Network
        is QuotaError.RateLimited -> ClaudeLoginFailure.RateLimited
        is QuotaError.ProviderUnavailable -> ClaudeLoginFailure.ProviderUnavailable
        is QuotaError.ResponseInvalid -> ClaudeLoginFailure.ResponseInvalid
    }

    private fun fail(failure: ClaudeLoginFailure) {
        update { it.copy(phase = ClaudeLoginPhase.Failed, failure = failure) }
    }

    private fun update(transform: (ClaudeLoginUiState) -> ClaudeLoginUiState) {
        _uiState.value = transform(_uiState.value)
    }

    companion object {
        /** Generous enough for a password manager, 2FA and a slow network. */
        const val SIGN_IN_TIMEOUT_MILLIS = 10 * 60 * 1000L

        fun factory(
            connector: ClaudeConnect,
            expectedProviderAccountId: ProviderAccountId?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClaudeLoginViewModel(
                        connector = connector,
                        expectedProviderAccountId = expectedProviderAccountId,
                    ) as T
            }
    }
}
