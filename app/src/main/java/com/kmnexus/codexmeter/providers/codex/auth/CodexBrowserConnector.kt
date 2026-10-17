package com.kmnexus.codexmeter.providers.codex.auth

import com.kmnexus.codexmeter.domain.model.ProviderAccount
import com.kmnexus.codexmeter.domain.model.ProviderAccountId
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.codex.session.CodexSessionPayload
import com.kmnexus.codexmeter.providers.common.auth.PkceSession

/** Exchanges and stores a Codex authorization code from the browser sign-in; see [CodexBrowserConnector]. */
fun interface CodexBrowserConnect {
    suspend fun connect(
        code: String,
        pkce: PkceSession,
        redirectUri: String,
        expectedProviderAccountId: ProviderAccountId?,
    ): CodexBrowserConnector.ConnectResult
}

/**
 * Turns an authorization code from the browser sign-in into a stored Codex account, with the same
 * token exchange and import as the device-code sign-in: the code is exchanged with the PKCE
 * verifier, the session is validated against the usage endpoint, and only then the account, its
 * first snapshot and the encrypted session are committed together. Nothing is written when a step
 * fails.
 */
class CodexBrowserConnector(
    private val exchange: suspend (CodexOAuthTokenExchangeRequest) -> CodexOAuthTokenExchanger.Result,
    private val prepare: suspend (CodexSessionPayload) -> CodexSessionImporter.PrepareResult,
    private val commit: suspend (CodexSessionImporter.PreparedImport) -> CodexSessionImporter.Result,
) : CodexBrowserConnect {
    /**
     * @param expectedProviderAccountId the ChatGPT account id when re-authenticating an existing
     *   account; signing in to another account then returns [ConnectResult.AccountMismatch].
     */
    override suspend fun connect(
        code: String,
        pkce: PkceSession,
        redirectUri: String,
        expectedProviderAccountId: ProviderAccountId?,
    ): ConnectResult {
        val request = CodexOAuthTokenExchangeRequest(
            authorizationCode = OAuthAuthorizationCode(code),
            pkceVerifier = PkceVerifier(pkce.codeVerifier),
            redirectUri = redirectUri,
        )
        val session = when (val exchanged = exchange(request)) {
            is CodexOAuthTokenExchanger.Result.Failure -> return ConnectResult.Failed(exchanged.error, ConnectStage.Exchange)
            is CodexOAuthTokenExchanger.Result.Success -> exchanged.session
        }
        if (expectedProviderAccountId != null && session.accountId != expectedProviderAccountId.value) {
            return ConnectResult.AccountMismatch(signedInEmail = session.accountEmail)
        }
        val prepared = when (val validated = prepare(session)) {
            is CodexSessionImporter.PrepareResult.Failure -> return ConnectResult.Failed(validated.quotaError, ConnectStage.Validate)
            is CodexSessionImporter.PrepareResult.Success -> validated.preparedImport
        }
        return when (val saved = commit(prepared)) {
            is CodexSessionImporter.Result.Failure -> ConnectResult.Failed(error = null, stage = ConnectStage.Persist)
            is CodexSessionImporter.Result.Success -> ConnectResult.Connected(saved.account)
        }
    }

    sealed interface ConnectResult {
        data class Connected(val account: ProviderAccount) : ConnectResult

        /** The browser session belongs to another ChatGPT account than the one being re-authenticated. */
        data class AccountMismatch(val signedInEmail: String?) : ConnectResult

        /** [error] is null for a local storage failure ([ConnectStage.Persist]). */
        data class Failed(val error: QuotaError?, val stage: ConnectStage) : ConnectResult
    }

    enum class ConnectStage { Exchange, Validate, Persist }
}
