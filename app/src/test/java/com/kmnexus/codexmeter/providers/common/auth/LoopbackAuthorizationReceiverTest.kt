package com.kmnexus.codexmeter.providers.common.auth

import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackPages
import com.kmnexus.codexmeter.providers.common.auth.LoopbackAuthorizationReceiver.CallbackResult
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoopbackAuthorizationReceiverTest {
    private val state = "expected-state-value"
    private val pages = CallbackPages.simple(
        successTitle = "Signed in",
        successMessage = "Close this tab",
        failureTitle = "Not signed in",
        failureMessage = "Try again",
    )
    private var receiver: LoopbackAuthorizationReceiver? = null

    @After
    fun tearDown() {
        receiver?.close()
    }

    @Test
    fun `valid callback returns the code and the page never echoes it`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }

        val response = get(receiver.port, "/callback?code=secret-code-123&state=$state")

        val callback = result.await()
        assertTrue(callback is CallbackResult.Code)
        assertEquals("secret-code-123", (callback as CallbackResult.Code).code)
        assertTrue(response.startsWith("HTTP/1.1 200"))
        assertTrue(response.contains("Signed in"))
        assertFalse(response.contains("secret-code-123"))
        assertFalse(response.contains(state))
        assertTrue(response.contains("Cache-Control: no-store"))
        assertTrue(response.contains("Content-Security-Policy: default-src 'none'"))
        assertFalse(callback.toString().contains("secret-code-123"))
    }

    @Test
    fun `wrong state is rejected and the real callback is still accepted`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }

        val forged = get(receiver.port, "/callback?code=injected&state=not-the-state")
        val missingState = get(receiver.port, "/callback?code=injected")
        val real = get(receiver.port, "/callback?code=real-code&state=$state")

        assertTrue(forged.startsWith("HTTP/1.1 400"))
        assertTrue(missingState.startsWith("HTTP/1.1 400"))
        assertTrue(real.startsWith("HTTP/1.1 200"))
        assertEquals("real-code", (result.await() as CallbackResult.Code).code)
    }

    @Test
    fun `other paths and methods are refused without ending the attempt`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }

        val favicon = get(receiver.port, "/favicon.ico")
        val post = request(receiver.port, "POST /callback?code=x&state=$state HTTP/1.1\r\nHost: localhost\r\n\r\n")
        val garbage = request(receiver.port, "nonsense\r\n\r\n")
        val real = get(receiver.port, "/callback?code=ok&state=$state")

        assertTrue(favicon.startsWith("HTTP/1.1 404"))
        assertTrue(post.startsWith("HTTP/1.1 405"))
        assertTrue(garbage.startsWith("HTTP/1.1 400"))
        assertTrue(real.startsWith("HTTP/1.1 200"))
        assertEquals("ok", (result.await() as CallbackResult.Code).code)
    }

    @Test
    fun `repeated parameters are treated as an invalid response`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }

        val duplicated = get(receiver.port, "/callback?code=a&code=b&state=$state")
        val real = get(receiver.port, "/callback?code=b&state=$state")

        assertTrue(duplicated.startsWith("HTTP/1.1 400"))
        assertTrue(real.startsWith("HTTP/1.1 200"))
        assertEquals("b", (result.await() as CallbackResult.Code).code)
    }

    @Test
    fun `provider error with the right state ends the attempt with a sanitized code`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }

        val response = get(receiver.port, "/callback?error=access_denied%3Cscript%3E&error_description=no&state=$state")

        assertTrue(response.startsWith("HTTP/1.1 200"))
        assertTrue(response.contains("Not signed in"))
        assertEquals(CallbackResult.ProviderError("access_deniedscript"), result.await())
    }

    @Test
    fun `provider error with a forged state is ignored`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 1_500) }

        val response = get(receiver.port, "/callback?error=access_denied&state=forged")

        assertTrue(response.startsWith("HTTP/1.1 400"))
        assertEquals(CallbackResult.TimedOut, result.await())
    }

    @Test
    fun `callback without a code is reported as missing code`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }

        get(receiver.port, "/callback?state=$state")

        assertEquals(CallbackResult.ProviderError(LoopbackAuthorizationReceiver.MISSING_CODE), result.await())
    }

    @Test
    fun `an idle pre-connection does not delay the real callback`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }
        val idle = withContext(Dispatchers.IO) { Socket(InetAddress.getByName("127.0.0.1"), receiver.port) }
        try {
            val startedAt = System.nanoTime()
            get(receiver.port, "/callback?code=fast&state=$state")
            assertEquals("fast", (result.await() as CallbackResult.Code).code)
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue("callback took $elapsedMillis ms", elapsedMillis < 3_000)
        } finally {
            withContext(Dispatchers.IO) { idle.close() }
        }
    }

    @Test
    fun `timeout closes the port`() = runBlocking {
        val receiver = start()
        val port = receiver.port

        assertEquals(CallbackResult.TimedOut, receiver.awaitResult(timeoutMillis = 300))

        delay(50)
        try {
            withContext(Dispatchers.IO) { Socket(InetAddress.getByName("127.0.0.1"), port).close() }
            fail("port $port should be closed after the attempt")
        } catch (_: ConnectException) {
            // expected
        }
    }

    @Test
    fun `port is closed after a successful callback`() = runBlocking {
        val receiver = start()
        val result = async { receiver.awaitResult(timeoutMillis = 10_000) }
        get(receiver.port, "/callback?code=one&state=$state")
        result.await()

        delay(50)
        val second = runCatching { get(receiver.port, "/callback?code=two&state=$state") }
        assertTrue(second.isFailure || second.getOrNull().orEmpty().isEmpty())
    }

    @Test
    fun `query parsing decodes values and rejects duplicates`() {
        assertEquals(
            mapOf("code" to "a b+c", "state" to "x/y"),
            LoopbackAuthorizationReceiver.parseQuery("code=a+b%2Bc&state=x%2Fy"),
        )
        assertNull(LoopbackAuthorizationReceiver.parseQuery("state=1&state=2"))
        assertNull(LoopbackAuthorizationReceiver.parseQuery("code=%zz"))
        assertEquals(emptyMap<String, String>(), LoopbackAuthorizationReceiver.parseQuery(""))
    }

    @Test
    fun `error codes keep only token characters`() {
        assertEquals("invalid_scope", LoopbackAuthorizationReceiver.sanitizeErrorCode("invalid_scope"))
        assertEquals("unknown_error", LoopbackAuthorizationReceiver.sanitizeErrorCode("<>\"'"))
        assertEquals(64, LoopbackAuthorizationReceiver.sanitizeErrorCode("a".repeat(500)).length)
    }

    @Test
    fun `empty state is refused`() {
        try {
            LoopbackAuthorizationReceiver.start(expectedState = "", pages = pages)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private fun start(): LoopbackAuthorizationReceiver =
        LoopbackAuthorizationReceiver.start(expectedState = state, pages = pages).also { receiver = it }

    private suspend fun get(port: Int, target: String): String =
        request(port, "GET $target HTTP/1.1\r\nHost: localhost:$port\r\nConnection: close\r\n\r\n")

    private suspend fun request(port: Int, raw: String): String = withContext(Dispatchers.IO) {
        try {
            Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
                socket.soTimeout = 5_000
                socket.getOutputStream().apply {
                    write(raw.toByteArray(Charsets.US_ASCII))
                    flush()
                }
                socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
        } catch (exception: IOException) {
            throw exception
        }
    }
}
