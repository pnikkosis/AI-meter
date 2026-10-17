package com.kmnexus.codexmeter.providers.codex.auth

import com.kmnexus.codexmeter.core.network.ProviderHttpClient
import com.kmnexus.codexmeter.data.secure.ProviderSessionEnvelope
import com.kmnexus.codexmeter.domain.account.RemoteRevocation
import com.kmnexus.codexmeter.providers.codex.CodexSessionCipher
import com.kmnexus.codexmeter.providers.codex.session.CodexSessionPayload
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class CodexTokenRevokerTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `revokes the refresh token with the client id like codex logout`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())

        val outcome = revoker(session(refreshToken = "refresh-secret")).revoke(envelope())

        assertEquals(RemoteRevocation.Revoked, outcome)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/oauth/revoke", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()) as JsonObject
        assertEquals(
            mapOf(
                "token" to "refresh-secret",
                "token_type_hint" to "refresh_token",
                "client_id" to "app_EMoamEEZ73f0CkXaXp7hrann",
            ),
            body.mapValues { it.value.jsonPrimitive.content },
        )
        assertFalse(request.headers["Authorization"] != null)
    }

    @Test
    fun `falls back to the access token without client id`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())

        revoker(session(refreshToken = "")).revoke(envelope())

        val body = Json.parseToJsonElement(server.takeRequest().body?.utf8().orEmpty()) as JsonObject
        assertEquals(
            mapOf("token" to "access-secret", "token_type_hint" to "access_token"),
            body.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun `error answers, unreachable server and unreadable sessions are failures`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_token"}""").build())
        assertEquals(RemoteRevocation.Failed, revoker(session()).revoke(envelope()))

        val unreadable = CodexTokenRevoker(
            httpClient = ProviderHttpClient(),
            sessionCipher = FakeCipher(null),
            endpointUrl = server.url("/oauth/revoke").toString(),
        )
        assertEquals(RemoteRevocation.Failed, unreadable.revoke(envelope()))

        val url = server.url("/oauth/revoke").toString()
        server.close()
        val offline = CodexTokenRevoker(ProviderHttpClient(), FakeCipher(session()), endpointUrl = url)
        assertEquals(RemoteRevocation.Failed, offline.revoke(envelope()))
    }

    @Test
    fun `a session without tokens has nothing to revoke`() = runTest {
        assertEquals(
            RemoteRevocation.NothingToRevoke,
            revoker(session(accessToken = "", refreshToken = "")).revoke(envelope()),
        )
        assertEquals(0, server.requestCount)
    }

    private fun revoker(session: CodexSessionPayload) = CodexTokenRevoker(
        httpClient = ProviderHttpClient(),
        sessionCipher = FakeCipher(session),
        endpointUrl = server.url("/oauth/revoke").toString(),
    )

    private fun session(accessToken: String = "access-secret", refreshToken: String = "refresh-secret") =
        CodexSessionPayload(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = null,
            accountId = "account-1",
            lastRefresh = Instant.parse("2026-10-17T00:00:00Z"),
        )

    private fun envelope() = ProviderSessionEnvelope(
        providerId = "codex",
        localAccountId = "codex-1",
        providerAccountId = "account-1",
        schemaVersion = 1,
        payloadCiphertext = byteArrayOf(1),
        payloadNonce = byteArrayOf(2),
        createdAt = "2026-10-17T00:00:00Z",
        updatedAt = "2026-10-17T00:00:00Z",
    )

    private class FakeCipher(private val session: CodexSessionPayload?) : CodexSessionCipher {
        override fun decrypt(envelope: ProviderSessionEnvelope): Result<CodexSessionPayload> =
            session?.let { Result.success(it) } ?: Result.failure(IllegalStateException("bad"))

        override fun encrypt(
            session: CodexSessionPayload,
            envelope: ProviderSessionEnvelope,
            updatedAt: Instant,
        ): ProviderSessionEnvelope = envelope
    }
}
