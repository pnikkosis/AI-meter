package com.kmnexus.codexmeter.notification

import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.domain.refresh.QuotaError

class AccountErrorPolicy(
    private val repeatedFailureThreshold: Int = REPEATED_FAILURE_THRESHOLD,
) {
    fun evaluate(
        state: CurrentQuotaState,
        consecutiveFailureCount: Int,
    ): AccountErrorNotificationEvent? =
        when {
            state.status == CurrentQuotaStatus.AuthRequired ->
                AccountErrorNotificationEvent(AccountErrorNotificationReason.AuthRequired)
            // Being offline is shown on the widget and in the app; background refresh keeps running
            // without a network, so counting those attempts would notify on every flight or tunnel.
            consecutiveFailureCount >= repeatedFailureThreshold && state.error !is QuotaError.Network ->
                AccountErrorNotificationEvent(AccountErrorNotificationReason.RepeatedRefreshFailure)
            else -> null
        }

    companion object {
        const val REPEATED_FAILURE_THRESHOLD = 3
    }
}
