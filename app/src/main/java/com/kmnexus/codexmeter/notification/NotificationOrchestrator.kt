package com.kmnexus.codexmeter.notification

import android.app.PendingIntent
import androidx.annotation.StringRes
import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaState
import com.kmnexus.codexmeter.domain.quota.CurrentQuotaStatus
import com.kmnexus.codexmeter.providers.ProviderRegistry

data class NotificationRequestOptions(
    val notificationPermissionAvailable: Boolean,
    val statusNotificationEnabled: Boolean = false,
    val quotaAlertsEnabled: Boolean = true,
    val accountErrorsEnabled: Boolean = true,
)

data class NotificationText(
    @field:StringRes val resourceId: Int,
    val formatArgs: List<Any> = emptyList(),
)

data class NotificationPendingIntentMetadata(
    val requestCode: Int,
    val flags: Int,
    val destination: NotificationDestination = NotificationDestination.Home,
    val externalUrl: String? = null,
)

enum class NotificationDestination {
    Home,
    AddAccount,
    ExternalUrl,
}

enum class NotificationActionType {
    CopyDeviceCode,
}

data class NotificationActionIntentMetadata(
    val requestCode: Int,
    val flags: Int,
    val action: NotificationActionType,
    val attemptId: String? = null,
)

data class NotificationAction(
    val title: NotificationText,
    val intent: NotificationActionIntentMetadata,
)

enum class AccountErrorNotificationReason {
    AuthRequired,
    RepeatedRefreshFailure,
}

data class AccountErrorNotificationEvent(
    val reason: AccountErrorNotificationReason,
)

data class NotificationRequest(
    val notificationId: Int,
    val channelId: String,
    val title: NotificationText,
    val body: NotificationText,
    val pendingIntent: NotificationPendingIntentMetadata,
    val ongoing: Boolean,
    val actions: List<NotificationAction> = emptyList(),
    val timeoutAfterMillis: Long? = null,
)

class NotificationOrchestrator {
    /** Alert and account-error requests for [state]; the status notification comes from [statusRequest]. */
    fun buildRequests(
        state: CurrentQuotaState,
        alertEvents: List<QuotaAlertEvent> = emptyList(),
        options: NotificationRequestOptions,
        accountErrorEvent: AccountErrorNotificationEvent? = null,
    ): List<NotificationRequest> {
        if (!options.notificationPermissionAvailable) {
            return emptyList()
        }

        return buildList {
            if (options.quotaAlertsEnabled) {
                addAll(alertEvents.map(::quotaAlertRequest))
            }
            if (options.accountErrorsEnabled) {
                accountErrorRequest(state, accountErrorEvent)?.let(::add)
            }
        }
    }

    /** The persistent status notification for [states], the accounts it shows in display order. */
    fun statusRequest(states: List<CurrentQuotaState>): NotificationRequest =
        NotificationRequest(
            notificationId = STATUS_NOTIFICATION_ID,
            channelId = NotificationChannels.STATUS_CHANNEL_ID,
            title = StatusNotificationContent.title(states),
            body = StatusNotificationContent.body(states),
            pendingIntent = homePendingIntent(STATUS_PENDING_INTENT_REQUEST_CODE),
            ongoing = true,
        )

    /**
     * "<provider>·<account>" so an account error names the account it is about. Falls back to a
     * generic title when the state has no account.
     */
    private fun identityTitle(state: CurrentQuotaState, @StringRes fallbackResId: Int): NotificationText {
        val account = state.account ?: return NotificationText(resourceId = fallbackResId)
        return NotificationText(
            resourceId = R.string.notification_status_title_identity_format,
            formatArgs = listOf(
                ProviderRegistry.displayNameFor(account.providerId),
                account.displayName,
            ),
        )
    }

    private fun quotaAlertRequest(event: QuotaAlertEvent): NotificationRequest =
        when (event.level) {
            AlertLevel.Caution -> NotificationRequest(
                notificationId = quotaAlertNotificationId(event),
                channelId = NotificationChannels.QUOTA_ALERTS_CHANNEL_ID,
                title = NotificationText(
                    resourceId = R.string.notification_alert_warning_title_percent_format,
                    formatArgs = listOf(event.remainingText),
                ),
                body = NotificationText(resourceId = R.string.notification_alert_warning_body),
                pendingIntent = homePendingIntent(QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE),
                ongoing = false,
            )
            AlertLevel.Warning -> NotificationRequest(
                notificationId = quotaAlertNotificationId(event),
                channelId = NotificationChannels.QUOTA_ALERTS_CHANNEL_ID,
                title = NotificationText(
                    resourceId = R.string.notification_alert_warning_title_percent_format,
                    formatArgs = listOf(event.remainingText),
                ),
                body = NotificationText(resourceId = R.string.notification_alert_warning_body),
                pendingIntent = homePendingIntent(QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE),
                ongoing = false,
            )
            AlertLevel.Limit -> NotificationRequest(
                notificationId = quotaAlertNotificationId(event),
                channelId = NotificationChannels.QUOTA_ALERTS_CHANNEL_ID,
                title = NotificationText(resourceId = R.string.notification_alert_limit_title),
                body = NotificationText(resourceId = R.string.notification_alert_limit_body),
                pendingIntent = homePendingIntent(QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE),
                ongoing = false,
            )
        }

    private fun accountErrorRequest(
        state: CurrentQuotaState,
        accountErrorEvent: AccountErrorNotificationEvent?,
    ): NotificationRequest? {
        val reason = when {
            state.status == CurrentQuotaStatus.AuthRequired -> AccountErrorNotificationReason.AuthRequired
            accountErrorEvent?.reason == AccountErrorNotificationReason.RepeatedRefreshFailure ->
                AccountErrorNotificationReason.RepeatedRefreshFailure
            else -> return null
        }
        val bodyResId = when (reason) {
            AccountErrorNotificationReason.AuthRequired -> R.string.notification_account_error_body_auth_required
            AccountErrorNotificationReason.RepeatedRefreshFailure -> R.string.notification_account_error_body_refresh_failed
        }

        return NotificationRequest(
            notificationId = accountErrorNotificationId(state),
            channelId = NotificationChannels.ACCOUNT_ERRORS_CHANNEL_ID,
            title = identityTitle(state, fallbackResId = R.string.notification_account_error_title),
            body = NotificationText(resourceId = bodyResId),
            pendingIntent = homePendingIntent(ACCOUNT_ERROR_PENDING_INTENT_REQUEST_CODE),
            ongoing = false,
        )
    }

    private fun quotaAlertNotificationId(event: QuotaAlertEvent): Int =
        stableNotificationId(
            base = QUOTA_ALERT_NOTIFICATION_ID_BASE,
            event.key.providerId.value,
            event.key.localAccountId.value,
            event.windowId.value,
            event.level.name,
        )

    private fun accountErrorNotificationId(state: CurrentQuotaState): Int {
        val account = state.account ?: return ACCOUNT_ERROR_NOTIFICATION_ID_BASE
        return stableNotificationId(
            base = ACCOUNT_ERROR_NOTIFICATION_ID_BASE,
            account.providerId.value,
            account.localAccountId.value,
        )
    }

    private fun stableNotificationId(
        base: Int,
        vararg parts: String,
    ): Int =
        base + Math.floorMod(parts.joinToString(separator = "|").hashCode(), NOTIFICATION_ID_BUCKET_SIZE)

    private fun homePendingIntent(requestCode: Int): NotificationPendingIntentMetadata =
        NotificationPendingIntentMetadata(
            requestCode = requestCode,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val STATUS_NOTIFICATION_ID = 1_001
        const val AUTH_LOGIN_NOTIFICATION_ID = 3_002
        private const val QUOTA_ALERT_NOTIFICATION_ID_BASE = 20_000
        private const val ACCOUNT_ERROR_NOTIFICATION_ID_BASE = 30_000
        private const val NOTIFICATION_ID_BUCKET_SIZE = 10_000
        private const val STATUS_PENDING_INTENT_REQUEST_CODE = 11
        private const val QUOTA_ALERT_PENDING_INTENT_REQUEST_CODE = 21
        private const val ACCOUNT_ERROR_PENDING_INTENT_REQUEST_CODE = 31
    }
}
