package com.kmnexus.codexmeter.providers.common.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceSessionTest {
    @Test
    fun `S256 challenge matches the RFC 7636 appendix B example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceSession.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `generated verifier and state are 43 url-safe characters and fresh per attempt`() {
        val urlSafe = Regex("^[A-Za-z0-9_-]{43}$")
        val first = PkceSession.create()
        val second = PkceSession.create()

        assertTrue(urlSafe.matches(first.codeVerifier))
        assertTrue(urlSafe.matches(first.state))
        assertEquals(PkceSession.challengeFor(first.codeVerifier), first.codeChallenge)
        assertNotEquals(first.codeVerifier, first.state)
        assertNotEquals(first.codeVerifier, second.codeVerifier)
        assertNotEquals(first.state, second.state)
        assertEquals("S256", PkceSession.CHALLENGE_METHOD)
    }

    @Test
    fun `toString never reveals the verifier or the state`() {
        val session = PkceSession.create()
        val text = session.toString()

        assertFalse(text.contains(session.codeVerifier))
        assertFalse(text.contains(session.state))
    }

    @Test
    fun `state comparison requires an exact match`() {
        assertTrue(constantTimeEquals("abc", "abc"))
        assertFalse(constantTimeEquals("abc", "abd"))
        assertFalse(constantTimeEquals("abc", "ab"))
        assertFalse(constantTimeEquals("abc", null))
        assertFalse(constantTimeEquals("abc", ""))
    }
}
