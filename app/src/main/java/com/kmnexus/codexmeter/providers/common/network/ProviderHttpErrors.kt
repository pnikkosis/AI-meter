package com.kmnexus.codexmeter.providers.common.network

import com.kmnexus.codexmeter.core.network.ProviderHttpResponse
import com.kmnexus.codexmeter.core.network.retryAfterSeconds
import com.kmnexus.codexmeter.domain.refresh.QuotaError

/**
 * True when the answer is a web page instead of the API's JSON: Cloudflare marks its bot challenges
 * with `cf-mitigated`, and an HTML body means something in front of the API answered. A 403 like
 * that says nothing about the credential.
 */
fun ProviderHttpResponse.isWebPage(): Boolean =
    header("cf-mitigated") != null ||
        header("Content-Type")?.trim()?.startsWith("text/html", ignoreCase = true) == true

/**
 * Error for an answer that is neither a success nor a refused credential. The server was reached,
 * so it is never "no connection": 429 is rate limiting, anything else is the provider's error.
 */
fun ProviderHttpResponse.unexpectedStatusError(diagnosticsDigest: String): QuotaError =
    if (statusCode == QuotaError.HTTP_TOO_MANY_REQUESTS) {
        QuotaError.RateLimited(diagnosticsDigest = diagnosticsDigest, retryAfterSeconds = retryAfterSeconds())
    } else {
        QuotaError.ProviderUnavailable(httpStatus = statusCode, diagnosticsDigest = diagnosticsDigest)
    }
