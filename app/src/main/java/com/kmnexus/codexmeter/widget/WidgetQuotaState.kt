package com.kmnexus.codexmeter.widget

import java.time.Instant

enum class WidgetQuotaStatus {
    NoAccount,
    Fresh,
    PossiblyStale,
    Expired,
    AuthRequired,
    ErrorWithLastKnownGood,
    NoData,
}

enum class WidgetQuotaTone {
    Neutral,
    Success,
    Warning,
    Danger,
}

enum class WidgetClickTarget {
    Home,
    AddAccount,
}

/** 微件上的一个额度字段（= 一个可展示的额度窗口）。余额型显示金额，其余显示剩余百分比。 */
data class WidgetField(
    val windowId: String,
    val isBalance: Boolean,
    val percent: Int?,
    val balanceAmount: String?,
    val balanceCurrency: String?,
    val resetAt: Instant?,
    val tone: WidgetQuotaTone,
    // Provider-reported window duration. Positional ids (Codex "five_hour"/"weekly") pick their
    // label from this instead of the slot they arrived in.
    val limitWindowSeconds: Int? = null,
    /** The provider's own name for a meter with no label of its own here; see quotaWindowLabelName. */
    val labelName: String? = null,
)

data class WidgetQuotaState(
    val status: WidgetQuotaStatus,
    val providerName: String,
    val providerId: String?,
    val localAccountId: String?,
    val accountName: String?,
    val tone: WidgetQuotaTone,
    val clickTarget: WidgetClickTarget,
    val fields: List<WidgetField> = emptyList(),
    // true = 微件未选账号，渲染引导态而非数据。
    val isUnconfigured: Boolean = false,
    // 引导态时区分「无任何账号」(false) 与「有账号待配置」(true)。
    val hasAccounts: Boolean = true,
    /** When the shown values were fetched from the provider; null when there is no snapshot. */
    val lastUpdatedAt: Instant? = null,
    /** `QuotaError.safeMessageKey` of the latest failed refresh, to name the kind of error shown. */
    val errorMessageKey: String? = null,
)
