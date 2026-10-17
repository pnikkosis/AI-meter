package com.kmnexus.codexmeter.domain.refresh

/**
 * Why a quota refresh failed. Each kind maps to a distinct user-facing message so that an error is
 * never presented as usage data (and never as 0%): the last successful snapshot, if any, is kept and
 * shown as stale next to the error.
 */
sealed class QuotaError {
    abstract val safeMessageKey: String
    abstract val httpStatus: Int?
    abstract val diagnosticsDigest: String?
    abstract val retryable: Boolean
    abstract val userActionRequired: Boolean

    /** The provider no longer accepts the stored session: the user has to sign in again. */
    data class AuthRequired(
        override val httpStatus: Int?,
        override val diagnosticsDigest: String?,
    ) : QuotaError() {
        override val safeMessageKey: String = "error_auth_required"
        override val retryable: Boolean = false
        override val userActionRequired: Boolean = true
    }

    /** No HTTP answer was received (offline, DNS, TLS failure, timeout). */
    data class Network(
        override val diagnosticsDigest: String?,
    ) : QuotaError() {
        override val safeMessageKey: String = "error_network"
        override val httpStatus: Int? = null
        override val retryable: Boolean = true
        override val userActionRequired: Boolean = false
    }

    /**
     * The provider answered HTTP 429. [retryAfterSeconds] comes from `Retry-After` when sent. Not
     * [retryable]: background work waits for its next period instead of retrying into the limit.
     */
    data class RateLimited(
        override val diagnosticsDigest: String?,
        val retryAfterSeconds: Long? = null,
    ) : QuotaError() {
        override val safeMessageKey: String = "error_rate_limited"
        override val httpStatus: Int? = HTTP_TOO_MANY_REQUESTS
        override val retryable: Boolean = false
        override val userActionRequired: Boolean = false
    }

    /** The provider answered with a server error or an unexpected status (5xx, 3xx, 404, …). */
    data class ProviderUnavailable(
        override val httpStatus: Int?,
        override val diagnosticsDigest: String?,
    ) : QuotaError() {
        override val safeMessageKey: String = "error_provider_unavailable"
        override val retryable: Boolean = true
        override val userActionRequired: Boolean = false
    }

    /**
     * A successful HTTP answer whose body is not usable quota data (format change, no windows). An
     * immediate retry would get the same answer, so it is not [retryable].
     */
    data class ResponseInvalid(
        override val diagnosticsDigest: String?,
    ) : QuotaError() {
        override val safeMessageKey: String = "error_response_invalid"
        override val httpStatus: Int? = null
        override val retryable: Boolean = false
        override val userActionRequired: Boolean = false
    }

    companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429

        /**
         * Rebuilds the error kind from what a [RefreshAttempt] persisted ([safeMessageKey] as the
         * error code). Unknown codes from older rows fall back to the HTTP status.
         */
        fun fromStored(
            errorCode: String?,
            httpStatus: Int?,
            userActionRequired: Boolean?,
            diagnosticsDigest: String?,
        ): QuotaError = when {
            userActionRequired == true || errorCode in AUTH_REQUIRED_ERROR_CODES ->
                AuthRequired(httpStatus = httpStatus, diagnosticsDigest = diagnosticsDigest)
            errorCode == "error_rate_limited" -> RateLimited(diagnosticsDigest = diagnosticsDigest)
            errorCode == "error_provider_unavailable" ->
                ProviderUnavailable(httpStatus = httpStatus, diagnosticsDigest = diagnosticsDigest)
            errorCode == "error_response_invalid" -> ResponseInvalid(diagnosticsDigest = diagnosticsDigest)
            errorCode == "error_network" -> Network(diagnosticsDigest = diagnosticsDigest)
            httpStatus == 401 || httpStatus == 403 ->
                AuthRequired(httpStatus = httpStatus, diagnosticsDigest = diagnosticsDigest)
            httpStatus == HTTP_TOO_MANY_REQUESTS -> RateLimited(diagnosticsDigest = diagnosticsDigest)
            httpStatus != null -> ProviderUnavailable(httpStatus = httpStatus, diagnosticsDigest = diagnosticsDigest)
            else -> Network(diagnosticsDigest = diagnosticsDigest)
        }

        private val AUTH_REQUIRED_ERROR_CODES = setOf(
            "error_auth_required",
            "auth_required",
            "reauth_required",
            "invalid_grant",
            "refresh_token_expired",
            "refresh_token_invalid",
            "refresh_token_invalidated",
            "refresh_token_reused",
            "refresh_token_revoked",
        )
    }
}
