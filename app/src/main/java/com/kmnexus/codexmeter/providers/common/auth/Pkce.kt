package com.kmnexus.codexmeter.providers.common.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One OAuth authorization attempt's secrets (RFC 7636 PKCE plus the CSRF `state`). They live only in
 * memory for the duration of a sign-in and are never logged or persisted.
 */
class PkceSession private constructor(
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
) {
    override fun toString(): String = "PkceSession(redacted)"

    companion object {
        const val CHALLENGE_METHOD = "S256"

        fun create(random: SecureRandom = SecureRandom()): PkceSession {
            // 32 random bytes -> 43 base64url characters: the RFC 7636 recommended verifier size.
            val verifier = randomUrlSafe(random, byteCount = 32)
            return PkceSession(
                codeVerifier = verifier,
                codeChallenge = challengeFor(verifier),
                state = randomUrlSafe(random, byteCount = 32),
            )
        }

        /** `BASE64URL(SHA256(ASCII(code_verifier)))` without padding (RFC 7636 §4.2, S256). */
        fun challengeFor(codeVerifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        private fun randomUrlSafe(random: SecureRandom, byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

/** Constant-time comparison for the returned `state`, so a mismatch reveals nothing through timing. */
internal fun constantTimeEquals(expected: String, actual: String?): Boolean {
    if (actual == null) return false
    return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))
}
