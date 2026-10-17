package com.kmnexus.codexmeter.core.network

/**
 * The only hosts the app sends HTTP requests to. Sign-in pages (auth.openai.com device page,
 * claude.com / claude.ai consent page) open in the user's browser and are not listed here.
 */
object ProviderHosts {
    /** OpenAI sign-in: device code, token exchange/refresh and token revocation. */
    const val OPENAI_AUTH = "auth.openai.com"

    /** Codex usage endpoint (`/backend-api/wham/usage`). */
    const val CHATGPT = "chatgpt.com"

    /** Claude OAuth token exchange and refresh. */
    const val CLAUDE_PLATFORM = "platform.claude.com"

    /** Claude usage endpoint (`/api/oauth/usage`). */
    const val ANTHROPIC_API = "api.anthropic.com"

    val ALL: Set<String> = setOf(OPENAI_AUTH, CHATGPT, CLAUDE_PLATFORM, ANTHROPIC_API)
}
