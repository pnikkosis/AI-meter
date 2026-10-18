package com.kmnexus.codexmeter.widget

import com.kmnexus.codexmeter.R
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetQuotaStatusLabelTest {
    @Test
    fun `fresh widget label uses the same words as Home for each level`() {
        assertEquals(R.string.home_quota_status_normal, freshState(QuotaLevel.Normal).statusLabelResId())
        assertEquals(R.string.home_quota_status_caution, freshState(QuotaLevel.Caution).statusLabelResId())
        assertEquals(R.string.home_quota_status_warning, freshState(QuotaLevel.Warning).statusLabelResId())
        assertEquals(R.string.home_window_depleted, freshState(QuotaLevel.Depleted).statusLabelResId())
        assertEquals(R.string.home_quota_status_unavailable, freshState(QuotaLevel.Unknown).statusLabelResId())
    }

    @Test
    fun `fresh balance account follows the same level words`() {
        assertEquals(
            R.string.home_quota_status_normal,
            freshState(QuotaLevel.Normal).copy(fields = listOf(balanceField())).statusLabelResId(),
        )
        assertEquals(
            R.string.home_quota_status_warning,
            freshState(QuotaLevel.Warning).copy(fields = listOf(balanceField())).statusLabelResId(),
        )
    }

    @Test
    fun `fresh quota account with unreadable percent still reads unavailable`() {
        val state = freshState(QuotaLevel.Unknown)
            .copy(fields = listOf(balanceField(isBalance = false)))

        assertEquals(R.string.home_quota_status_unavailable, state.statusLabelResId())
    }

    @Test
    fun `non-fresh statuses map to dedicated labels`() {
        assertEquals(R.string.widget_connect_codex, stateWith(WidgetQuotaStatus.NoAccount).statusLabelResId())
        assertEquals(R.string.widget_status_possibly_stale, stateWith(WidgetQuotaStatus.PossiblyStale).statusLabelResId())
        assertEquals(R.string.widget_status_expired, stateWith(WidgetQuotaStatus.Expired).statusLabelResId())
        assertEquals(R.string.widget_status_auth_required, stateWith(WidgetQuotaStatus.AuthRequired).statusLabelResId())
        assertEquals(R.string.widget_status_refresh_failed, stateWith(WidgetQuotaStatus.ErrorWithLastKnownGood).statusLabelResId())
        assertEquals(R.string.widget_status_no_data, stateWith(WidgetQuotaStatus.NoData).statusLabelResId())
    }

    @Test
    fun `a failed refresh names the kind of error, with or without old data`() {
        val cases = mapOf(
            "error_network" to R.string.widget_status_offline,
            "error_rate_limited" to R.string.widget_status_rate_limited,
            "error_provider_unavailable" to R.string.widget_status_provider_error,
            "error_response_invalid" to R.string.widget_status_unreadable,
            "error_unknown" to R.string.widget_status_refresh_failed,
        )
        cases.forEach { (key, expected) ->
            assertEquals(key, expected, stateWith(WidgetQuotaStatus.ErrorWithLastKnownGood).copy(errorMessageKey = key).statusLabelResId())
            assertEquals(key, expected, stateWith(WidgetQuotaStatus.NoData).copy(errorMessageKey = key).statusLabelResId())
        }
    }

    @Test
    fun `status-only widgets explain what to do instead of showing empty values`() {
        assertEquals(R.string.widget_message_sign_in_again, stateWith(WidgetQuotaStatus.AuthRequired).statusMessageResId())
        assertEquals(R.string.widget_message_no_data, stateWith(WidgetQuotaStatus.NoData).statusMessageResId())
        assertEquals(
            R.string.error_network,
            stateWith(WidgetQuotaStatus.NoData).copy(errorMessageKey = "error_network").statusMessageResId(),
        )
    }

    private fun freshState(level: QuotaLevel): WidgetQuotaState =
        stateWith(status = WidgetQuotaStatus.Fresh, level = level)

    private fun balanceField(isBalance: Boolean = true): WidgetField =
        WidgetField(
            windowId = "balance",
            isBalance = isBalance,
            percent = null,
            balanceAmount = null,
            balanceCurrency = null,
            resetAt = null,
            level = QuotaLevel.Unknown,
        )

    private fun stateWith(
        status: WidgetQuotaStatus,
        level: QuotaLevel = QuotaLevel.Unknown,
    ): WidgetQuotaState =
        WidgetQuotaState(
            status = status,
            providerName = "Codex",
            providerId = "codex",
            localAccountId = "local-1",
            accountName = "Codex Main",
            level = level,
            clickTarget = WidgetClickTarget.Home,
        )
}
