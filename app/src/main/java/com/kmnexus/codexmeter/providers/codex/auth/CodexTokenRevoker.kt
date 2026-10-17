package com.kmnexus.codexmeter.providers.codex.auth

import com.kmnexus.codexmeter.core.network.AppUserAgent
import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.data.secure.SessionRevoker
import com.kmnexus.codexmeter.domain.account.RemoteRevocation
import com.kmnexus.codexmeter.providers.codex.CodexSessionCipher
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Asks OpenAI to revoke the Codex sign-in when an account is disconnected, exactly like `codex
 * logout`: RFC 7009 revocation of the refresh token (with the client id), or of the access token
 * when no refresh token is stored. Best effort: the caller deletes the local data whatever happens.
 */
class CodexTokenRevoker(
    private val httpClient: ProviderHttpClient,
    private val sessionCipher: CodexSessionCipher,
    private val endpointUrl: String = CodexOAuthConfig.REVOKE_ENDPOINT_URL,
) : SessionRevoker {
    override suspend fun revoke(envelope: ProviderSessionEnvelope): RemoteRevocation {
        val session = sessionCipher.decrypt(envelope).getOrNull() ?: return RemoteRevocation.Failed
        val fields = when {
            session.refreshToken.isNotBlank() -> mapOf(
                "token" to session.refreshToken,
                "token_type_hint" to "refresh_token",
                "client_id" to CodexOAuthConfig.CLIENT_ID,
            )
            session.accessToken.isNotBlank() -> mapOf(
                "token" to session.accessToken,
                "token_type_hint" to "access_token",
            )
            else -> return RemoteRevocation.NothingToRevoke
        }
        return try {
            val response = httpClient.postJson(
                url = endpointUrl,
                jsonBody = JsonObject(fields.mapValues { JsonPrimitive(it.value) }).toString(),
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to AppUserAgent.DEFAULT,
                ),
            )
            if (response.statusCode in 200..299) RemoteRevocation.Revoked else RemoteRevocation.Failed
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            RemoteRevocation.Failed
        }
    }
}
