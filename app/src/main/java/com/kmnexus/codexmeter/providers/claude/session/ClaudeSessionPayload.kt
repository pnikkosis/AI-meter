package com.kmnexus.codexmeter.providers.claude.session

import kotlinx.serialization.Serializable

/**
 * Claude session kept on the device, serialized to JSON and then encrypted with an Android Keystore
 * AES-GCM key before it is written (see `PayloadCipher`). Never stored or logged in clear text.
 */
@Serializable
data class ClaudeSessionPayload(
    val accessToken: String,
    val refreshToken: String? = null,
    val tokenExpiresAtEpochSeconds: Long? = null,
    val organizationId: String? = null,
    val accountId: String? = null,
    val accountEmail: String? = null,
    /** Scopes the provider granted, as reported by the token endpoint. */
    val scope: String? = null,
) {
    override fun toString(): String = "ClaudeSessionPayload(redacted)"
}
