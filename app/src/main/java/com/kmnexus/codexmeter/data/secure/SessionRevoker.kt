package com.kmnexus.codexmeter.data.secure

import com.kmnexus.codexmeter.domain.account.RemoteRevocation
import com.kmnexus.codexmeter.domain.model.ProviderId

/** Revokes a stored sign-in at the provider, if the provider supports it. */
fun interface SessionRevoker {
    suspend fun revoke(envelope: ProviderSessionEnvelope): RemoteRevocation
}

/**
 * Dispatches to the provider's revoker. Providers without a revocation endpoint (Claude: Anthropic
 * publishes none, and the Claude Code CLI only deletes its local credentials on logout) report
 * [RemoteRevocation.NotSupported].
 */
class ProviderSessionRevoker(
    private val revokers: Map<ProviderId, SessionRevoker>,
) : SessionRevoker {
    override suspend fun revoke(envelope: ProviderSessionEnvelope): RemoteRevocation =
        revokers[ProviderId(envelope.providerId)]?.revoke(envelope) ?: RemoteRevocation.NotSupported
}
