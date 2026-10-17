package com.kmnexus.codexmeter.core.network

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class ProviderHttpResponse(
    val statusCode: Int,
    val body: String,
    /** Response headers keyed by lower-cased name (the last value when a header repeats). */
    val headers: Map<String, String> = emptyMap(),
) {
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]
}

/** `Retry-After` in seconds when the provider sent the delta-seconds form; HTTP dates are ignored. */
fun ProviderHttpResponse.retryAfterSeconds(): Long? =
    header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

/** Thrown before anything is sent when a URL is not HTTPS or not one of the allowed provider hosts. */
class DisallowedHostException(message: String) : IOException(message)

/**
 * Thin OkHttp wrapper used for every provider request.
 *
 * Redirects are never followed: a token or usage request that gets redirected comes back as its 3xx
 * response instead of silently re-sending the bearer token or request body to another URL. When
 * [allowedHosts] is set (always in the app; tests leave it null to reach MockWebServer), requests
 * are only issued over HTTPS to exactly those hosts. TLS uses the platform's default certificate
 * validation; nothing here customizes trust.
 */
class ProviderHttpClient(
    okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val allowedHosts: Set<String>? = null,
) {
    private val client = okHttpClient
        .newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(okHttpClient.callTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
        .connectTimeout(okHttpClient.connectTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
        .readTimeout(okHttpClient.readTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
        .writeTimeout(okHttpClient.writeTimeoutMillis.clampedTimeoutMillis(), TimeUnit.MILLISECONDS)
        .build()

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ProviderHttpResponse =
        withContext(ioDispatcher) {
            val requestBuilder = Request.Builder()
                .url(checkedUrl(url))
                .get()

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response -> response.toProviderResponse() }
        }

    suspend fun postForm(
        url: String,
        formFields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): ProviderHttpResponse =
        withContext(ioDispatcher) {
            val formBody = FormBody.Builder().apply {
                formFields.forEach { (name, value) ->
                    add(name, value)
                }
            }.build()
            val requestBuilder = Request.Builder()
                .url(checkedUrl(url))
                .post(formBody)

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response -> response.toProviderResponse() }
        }

    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
    ): ProviderHttpResponse =
        withContext(ioDispatcher) {
            val requestBuilder = Request.Builder()
                .url(checkedUrl(url))
                .post(jsonBody.toRequestBody(APPLICATION_JSON_MEDIA_TYPE))

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            client.newCall(requestBuilder.build()).execute().use { response -> response.toProviderResponse() }
        }

    private fun checkedUrl(url: String): HttpUrl {
        val parsed = url.toHttpUrl()
        val hosts = allowedHosts ?: return parsed
        if (!parsed.isHttps || parsed.host.lowercase(Locale.ROOT) !in hosts) {
            throw DisallowedHostException("Blocked request to a host outside the provider allowlist")
        }
        return parsed
    }

    private fun Response.toProviderResponse(): ProviderHttpResponse =
        ProviderHttpResponse(
            statusCode = code,
            body = body.string(),
            headers = headers.names().associate { name ->
                name.lowercase(Locale.ROOT) to headers[name].orEmpty()
            },
        )

    private fun Int.clampedTimeoutMillis(): Long =
        when {
            this == NO_TIMEOUT -> MAX_TIMEOUT_MILLIS
            this > MAX_TIMEOUT_MILLIS -> MAX_TIMEOUT_MILLIS
            else -> toLong()
        }

    companion object {
        /**
         * Client for credential-rotating requests (OAuth refresh grants). OkHttp silently replays a
         * request after a connection failure; for a refresh token the server has already consumed,
         * that replay burns the replacement credential — so transport retries stay off here.
         */
        fun noRetry(allowedHosts: Set<String>? = null): ProviderHttpClient = ProviderHttpClient(
            okHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
            allowedHosts = allowedHosts,
        )

        private const val NO_TIMEOUT = 0
        private const val MAX_TIMEOUT_MILLIS = 30_000L
        private val APPLICATION_JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
