package com.kmnexus.codexmeter.providers.codex.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decodes a device-code answer with the java.time of the Android version under test. Versions whose
 * java.time predates JDK 12 (Android 12 among them) only parse an `Instant` that ends in `Z`, while
 * the JVM of the unit tests also accepts offsets such as `+00:00`, so only a device or emulator
 * shows whether `expires_at` is understood.
 * The answer is served by an interceptor: nothing goes to the network.
 */
@RunWith(AndroidJUnit4::class)
class CodexDeviceCodeExpiryTest {

    private val now = Instant.parse("2026-10-17T10:00:00Z")

    @Test
    fun expiryWithAnOffsetIsUnderstood() {
        assertEquals(900, expiresInSecondsFor("2026-10-17T10:15:00.123456+00:00"))
    }

    @Test
    fun expiryInUtcIsUnderstood() {
        assertEquals(900, expiresInSecondsFor("2026-10-17T10:15:00Z"))
    }

    private fun expiresInSecondsFor(expiresAt: String): Int? = runBlocking {
        val body = """{"device_auth_id":"test-device-auth","user_code":"TEST-0000",""" +
            """"interval":"5","expires_at":"$expiresAt"}"""
        val cannedAnswer = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val client = CodexDeviceCodeClient(
            httpClient = ProviderHttpClient(okHttpClient = cannedAnswer),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        (client.requestDeviceCode() as? CodexDeviceCodeClient.Result.Success)?.value?.expiresInSeconds
    }
}
