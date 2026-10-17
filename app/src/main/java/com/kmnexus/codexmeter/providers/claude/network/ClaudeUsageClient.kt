package com.kmnexus.codexmeter.providers.claude.network

import com.kmnexus.codexmeter.core.network.AppUserAgent
import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.core.network.ProviderHttpResponse
import com.kmnexus.codexmeter.core.network.retryAfterSeconds
import com.kmnexus.codexmeter.domain.refresh.QuotaError
import com.kmnexus.codexmeter.providers.claude.auth.ClaudeOAuthConfig
import com.kmnexus.codexmeter.providers.claude.dto.ClaudeUsageResponseDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the signed-in account's subscription usage. This is the only Claude API call the app makes
 * with the access token; it is a GET and changes nothing on the account.
 *
 * The request identifies itself with the app's own User-Agent instead of imitating Claude Code.
 */
class ClaudeUsageClient(
    private val httpClient: ProviderHttpClient,
    private val url: String = ClaudeOAuthConfig.USAGE_URL,
    private val userAgent: String = AppUserAgent.DEFAULT,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchUsage(accessToken: String): Result {
        val response = try {
            httpClient.get(
                url = url,
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "Accept" to "application/json",
                    "anthropic-beta" to ClaudeOAuthConfig.OAUTH_BETA_HEADER,
                    "User-Agent" to userAgent,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            return Result.Failure(QuotaError.Network(diagnosticsDigest = "claude_usage_network"))
        }

        return when (val status = response.statusCode) {
            in 200..299 -> decodeSuccess(response.body)
            401 -> Result.Failure(
                QuotaError.AuthRequired(httpStatus = status, diagnosticsDigest = "claude_usage_http_401"),
            )
            403 -> Result.Failure(forbidden(response))
            QuotaError.HTTP_TOO_MANY_REQUESTS -> Result.Failure(
                QuotaError.RateLimited(
                    diagnosticsDigest = "claude_usage_http_429",
                    retryAfterSeconds = response.retryAfterSeconds(),
                ),
            )
            else -> Result.Failure(
                QuotaError.ProviderUnavailable(httpStatus = status, diagnosticsDigest = "claude_usage_http_$status"),
            )
        }
    }

    /**
     * A JSON `authentication_error` / `permission_error` means the token itself is not accepted
     * (revoked, or missing the scope) and signing in again is the fix. Any other 403 — for example
     * an HTML block page — is reported as the provider refusing the request, not as a lost session.
     */
    private fun forbidden(response: ProviderHttpResponse): QuotaError {
        val type = runCatching {
            (json.parseToJsonElement(response.body) as? JsonObject)
                ?.get("error")?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return if (type == "authentication_error" || type == "permission_error") {
            QuotaError.AuthRequired(httpStatus = 403, diagnosticsDigest = "claude_usage_http_403_$type")
        } else {
            QuotaError.ProviderUnavailable(httpStatus = 403, diagnosticsDigest = "claude_usage_http_403")
        }
    }

    private fun decodeSuccess(body: String): Result =
        try {
            Result.Success(json.decodeFromString<ClaudeUsageResponseDto>(body))
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Result.Failure(QuotaError.ResponseInvalid(diagnosticsDigest = "claude_usage_decode"))
        }

    sealed interface Result {
        data class Success(val dto: ClaudeUsageResponseDto) : Result
        data class Failure(val error: QuotaError) : Result
    }
}
