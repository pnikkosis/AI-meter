package com.kmnexus.codexmeter.providers.common.auth

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Waits for one OAuth authorization response; see [LoopbackAuthorizationReceiver]. */
interface AuthorizationCallbackReceiver : Closeable {
    /** Local port the redirect URI points to. */
    val port: Int

    /** Suspends until the response arrives or [timeoutMillis] passes; closes the receiver. */
    suspend fun awaitResult(timeoutMillis: Long): LoopbackAuthorizationReceiver.CallbackResult
}

/**
 * Receives the OAuth authorization response through a loopback redirect (RFC 8252 §7.3), for a
 * sign-in that runs in the user's own browser instead of an embedded WebView.
 *
 * - Listens on the loopback interface only (127.0.0.1, plus ::1 on the same port when available),
 *   never on an address reachable from the network, on an ephemeral port chosen by the OS.
 * - Only `GET <callbackPath>` is handled; any other request gets an error status and the receiver
 *   keeps waiting.
 * - A callback whose `state` does not match (compared in constant time) is answered with 400 and
 *   otherwise ignored, so another app on the device can neither inject a code nor abort the sign-in.
 * - The first callback carrying the expected `state` ends the attempt, with either the
 *   authorization `code` or the provider's `error`, and the listening sockets are closed.
 * - Responses never echo the code or the state, are `no-store`, and carry a CSP that blocks every
 *   subresource.
 */
class LoopbackAuthorizationReceiver private constructor(
    private val sockets: List<ServerSocket>,
    private val expectedState: String,
    private val callbackPath: String,
    private val pages: CallbackPages,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthorizationCallbackReceiver {
    /** The port the browser is redirected to; identical on the IPv4 and IPv6 loopback sockets. */
    override val port: Int = sockets.first().localPort

    private val openConnections = AtomicInteger(0)
    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    /**
     * Waits for the authorization response. Always closes the receiver before returning, so it
     * serves at most one sign-in attempt.
     */
    override suspend fun awaitResult(timeoutMillis: Long): CallbackResult = coroutineScope {
        val result = CompletableDeferred<CallbackResult>()
        val acceptJobs = sockets.map { server ->
            launch(ioDispatcher) { acceptLoop(server, result, this@coroutineScope) }
        }
        try {
            withTimeoutOrNull(timeoutMillis) { result.await() } ?: CallbackResult.TimedOut
        } finally {
            // Closing the sockets unblocks accept() and any pending read; the coroutines then end.
            close()
            acceptJobs.forEach { it.cancel() }
        }
    }

    override fun close() {
        sockets.forEach { runCatching { it.close() } }
        clients.forEach { runCatching { it.close() } }
    }

    private fun acceptLoop(
        server: ServerSocket,
        result: CompletableDeferred<CallbackResult>,
        scope: CoroutineScope,
    ) {
        while (!result.isCompleted && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: IOException) {
                return
            }
            if (openConnections.incrementAndGet() > MAX_CONCURRENT_CONNECTIONS) {
                openConnections.decrementAndGet()
                runCatching { client.close() }
                continue
            }
            clients += client
            // One coroutine per connection: a browser's idle pre-connection must not delay the real
            // callback that arrives on a second connection.
            scope.launch(ioDispatcher) {
                try {
                    client.use { socket ->
                        handle(socket, alreadyCompleted = result.isCompleted)?.let { result.complete(it) }
                    }
                } finally {
                    clients -= client
                    openConnections.decrementAndGet()
                }
            }
        }
    }

    private fun handle(socket: Socket, alreadyCompleted: Boolean): CallbackResult? {
        socket.soTimeout = READ_TIMEOUT_MILLIS
        val requestLine = try {
            readRequestLine(socket.getInputStream())
        } catch (_: IOException) {
            null
        }
        if (requestLine == null) {
            respond(socket, 400, pages.invalidRequest)
            return null
        }

        val parts = requestLine.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/")) {
            respond(socket, 400, pages.invalidRequest)
            return null
        }
        val (method, target) = parts
        if (target.substringBefore('?') != callbackPath) {
            respond(socket, 404, pages.invalidRequest)
            return null
        }
        if (method != "GET") {
            respond(socket, 405, pages.invalidRequest)
            return null
        }
        val params = parseQuery(target.substringAfter('?', ""))
        if (params == null || !constantTimeEquals(expectedState, params["state"])) {
            respond(socket, 400, pages.invalidRequest)
            return null
        }
        if (alreadyCompleted) {
            respond(socket, 409, pages.invalidRequest)
            return null
        }
        params["error"]?.let { error ->
            respond(socket, 200, pages.failure)
            return CallbackResult.ProviderError(sanitizeErrorCode(error))
        }
        val code = params["code"]
        if (code.isNullOrBlank() || code.length > MAX_CODE_LENGTH) {
            respond(socket, 400, pages.failure)
            return CallbackResult.ProviderError(MISSING_CODE)
        }
        respond(socket, 200, pages.success)
        return CallbackResult.Code(code)
    }

    private fun respond(socket: Socket, status: Int, page: String) {
        val body = page.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reasonPhrase(status)).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Pragma: no-cache\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Content-Security-Policy: $CONTENT_SECURITY_POLICY\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        runCatching {
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                write(body)
                flush()
            }
        }
    }

    /** Outcome of one sign-in attempt. [toString] never reveals the authorization code. */
    sealed interface CallbackResult {
        class Code(val code: String) : CallbackResult {
            override fun toString(): String = "Code(redacted)"
        }

        /** The provider redirected back with `error` (for example `access_denied`). */
        data class ProviderError(val error: String) : CallbackResult

        data object TimedOut : CallbackResult
    }

    /** Pre-rendered HTML pages shown in the browser tab after the redirect. */
    data class CallbackPages(
        val success: String,
        val failure: String,
        val invalidRequest: String,
    ) {
        companion object {
            fun simple(
                successTitle: String,
                successMessage: String,
                failureTitle: String,
                failureMessage: String,
            ): CallbackPages = CallbackPages(
                success = page(successTitle, successMessage),
                failure = page(failureTitle, failureMessage),
                invalidRequest = page(failureTitle, failureMessage),
            )

            private fun page(title: String, message: String): String =
                "<!doctype html><html><head><meta charset=\"utf-8\">" +
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                    "<title>${escapeHtml(title)}</title>" +
                    "<style>body{font-family:sans-serif;margin:2em;line-height:1.5}</style></head>" +
                    "<body><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p></body></html>"

            private fun escapeHtml(text: String): String = buildString(text.length) {
                text.forEach { char ->
                    when (char) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&#39;")
                        else -> append(char)
                    }
                }
            }
        }
    }

    companion object {
        const val MISSING_CODE = "missing_code"

        private const val READ_TIMEOUT_MILLIS = 5_000
        private const val MAX_REQUEST_LINE_BYTES = 8 * 1024
        private const val MAX_CODE_LENGTH = 4 * 1024
        private const val MAX_CONCURRENT_CONNECTIONS = 8
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

        /**
         * Binds the loopback sockets. Throws [IOException] when no loopback port can be opened.
         */
        fun start(
            expectedState: String,
            pages: CallbackPages,
            callbackPath: String = "/callback",
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): LoopbackAuthorizationReceiver {
            require(expectedState.isNotEmpty()) { "state must not be empty" }
            val ipv4 = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
            // Browsers may resolve `localhost` to ::1 first; listen there too when the device allows.
            val ipv6 = runCatching { ServerSocket(ipv4.localPort, BACKLOG, InetAddress.getByName("::1")) }.getOrNull()
            return LoopbackAuthorizationReceiver(
                sockets = listOfNotNull(ipv4, ipv6),
                expectedState = expectedState,
                callbackPath = callbackPath,
                pages = pages,
                ioDispatcher = ioDispatcher,
            )
        }

        private const val BACKLOG = 4

        /** Reads up to the first CRLF (or LF), bounded so a misbehaving client cannot exhaust memory. */
        internal fun readRequestLine(input: InputStream): String? {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < MAX_REQUEST_LINE_BYTES) {
                val next = input.read()
                if (next == -1) return null
                if (next == '\n'.code) {
                    return bytes.toString(Charsets.US_ASCII.name()).removeSuffix("\r")
                }
                bytes.write(next)
            }
            return null
        }

        /**
         * Decodes the query string. Returns null when it is malformed or repeats a parameter, which
         * RFC 6749 forbids for authorization responses.
         */
        internal fun parseQuery(query: String): Map<String, String>? {
            if (query.isEmpty()) return emptyMap()
            val result = LinkedHashMap<String, String>()
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val name = pair.substringBefore('=')
                val rawValue = pair.substringAfter('=', "")
                val decodedName = decode(name) ?: return null
                val decodedValue = decode(rawValue) ?: return null
                if (result.put(decodedName, decodedValue) != null) return null
            }
            return result
        }

        private fun decode(value: String): String? =
            runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()

        /** Keeps only a short token-like error code; the provider's free text is never kept. */
        internal fun sanitizeErrorCode(error: String): String =
            error.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
                .take(64)
                .ifEmpty { "unknown_error" }

        private fun reasonPhrase(status: Int): String = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            else -> "Error"
        }
    }
}
