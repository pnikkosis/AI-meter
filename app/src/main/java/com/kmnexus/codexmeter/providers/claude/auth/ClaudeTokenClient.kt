package com.kmnexus.codexmeter.providers.claude.auth

import com.kmnexus.codexmeter.core.network.AppUserAgent
import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.core.network.ProviderHttpResponse
import com.kmnexus.codexmeter.core.network.retryAfterSeconds
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Claude OAuth token endpoint: authorization-code exchange and refresh, with the JSON bodies the
 * Claude Code CLI sends. Tokens are returned to the caller only; nothing here logs or persists them.
 */
class ClaudeTokenClient(
    private val httpClient: ProviderHttpClient,
    private val tokenUrl: String = ClaudeOAuthConfig.TOKEN_URL,
    private val userAgent: String = AppUserAgent.DEFAULT,
) {
    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
        state: String,
    ): Result = post(
        fields = linkedMapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "client_id" to ClaudeOAuthConfig.CLIENT_ID,
            "code_verifier" to codeVerifier,
            "state" to state,
        ),
        stage = "exchange",
    )

    suspend fun refresh(refreshToken: String): Result = post(
        fields = linkedMapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to ClaudeOAuthConfig.CLIENT_ID,
            "scope" to ClaudeOAuthConfig.SCOPE,
        ),
        stage = "refresh",
    )

    private suspend fun post(fields: Map<String, String>, stage: String): Result {
        val body = JsonObject(fields.mapValues { JsonPrimitive(it.value) }).toString()
        val response = try {
            httpClient.postJson(
                url = tokenUrl,
                jsonBody = body,
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to userAgent,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            return Result.Failure(QuotaError.Network(diagnosticsDigest = "claude_token_${stage}_network"))
        }
        return when (response.statusCode) {
            in 200..299 -> parseTokens(response.body, stage)
            else -> Result.Failure(errorFor(response, stage))
        }
    }

    private fun parseTokens(body: String, stage: String): Result {
        val dto = try {
            json.decodeFromString<TokenResponseDto>(body)
        } catch (_: Exception) {
            null
        }
        val accessToken = dto?.accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.Failure(QuotaError.ResponseInvalid(diagnosticsDigest = "claude_token_${stage}_decode"))
        return Result.Success(
            ClaudeTokens(
                accessToken = accessToken,
                refreshToken = dto.refreshToken?.takeIf { it.isNotBlank() },
                expiresInSeconds = dto.expiresIn?.takeIf { it > 0 },
                scope = dto.scope,
                accountUuid = dto.account?.uuid?.takeIf { it.isNotBlank() },
                accountEmail = dto.account?.emailAddress?.trim()?.takeIf { it.isNotEmpty() },
                organizationUuid = dto.organization?.uuid?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun errorFor(response: ProviderHttpResponse, stage: String): QuotaError {
        val status = response.statusCode
        val code = oauthErrorCode(response.body)
        val digest = "claude_token_${stage}_http_$status" + (code?.let { "_$it" } ?: "")
        return when {
            status == QuotaError.HTTP_TOO_MANY_REQUESTS ->
                QuotaError.RateLimited(diagnosticsDigest = digest, retryAfterSeconds = response.retryAfterSeconds())
            // invalid_grant: the code or refresh token is expired, used or revoked -> sign in again.
            status in 400..499 -> QuotaError.AuthRequired(httpStatus = status, diagnosticsDigest = digest)
            else -> QuotaError.ProviderUnavailable(httpStatus = status, diagnosticsDigest = digest)
        }
    }

    /** OAuth `{"error":"invalid_grant"}` or Anthropic `{"error":{"type":"..."}}`, as a short token. */
    private fun oauthErrorCode(body: String): String? {
        val element = try {
            json.parseToJsonElement(body)
        } catch (_: Exception) {
            return null
        }
        val error: JsonElement = (element as? JsonObject)?.get("error") ?: return null
        val raw = (error as? JsonPrimitive)?.contentOrNull
            ?: runCatching { error.jsonObject["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        return raw?.filter { it.isLetterOrDigit() || it == '_' }?.take(40)?.takeIf { it.isNotEmpty() }
    }

    sealed interface Result {
        class Success(val tokens: ClaudeTokens) : Result {
            override fun toString(): String = "Success(redacted)"
        }

        data class Failure(val error: QuotaError) : Result
    }

    @Serializable
    private data class TokenResponseDto(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        val scope: String? = null,
        val account: AccountDto? = null,
        val organization: OrganizationDto? = null,
    )

    @Serializable
    private data class AccountDto(
        val uuid: String? = null,
        @SerialName("email_address") val emailAddress: String? = null,
    )

    @Serializable
    private data class OrganizationDto(
        val uuid: String? = null,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Token endpoint answer. [toString] is redacted so tokens cannot end up in logs by accident. */
class ClaudeTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
    val scope: String?,
    val accountUuid: String?,
    val accountEmail: String?,
    val organizationUuid: String?,
) {
    override fun toString(): String = "ClaudeTokens(redacted)"
}
