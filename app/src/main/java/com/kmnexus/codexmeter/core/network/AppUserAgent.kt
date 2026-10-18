package com.kmnexus.codexmeter.core.network

/**
 * The User-Agent every provider request carries.
 *
 * It identifies this app honestly instead of impersonating another client (no browser, CLI or
 * Claude Code strings), so provider-side logs show exactly which software made the call.
 */
object AppUserAgent {
    const val PRODUCT = "AIMeter"

    /** Used by clients built without an explicit version (unit tests, previews). */
    const val DEFAULT = "$PRODUCT/1.1.1 (Android)"

    fun forVersion(versionName: String): String = "$PRODUCT/${versionName.ifBlank { "unknown" }} (Android)"
}
