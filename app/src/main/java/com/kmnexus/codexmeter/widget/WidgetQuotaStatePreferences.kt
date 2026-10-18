package com.kmnexus.codexmeter.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kmnexus.codexmeter.domain.quota.QuotaLevel
import java.time.Instant

internal fun Preferences.toWidgetQuotaState(): WidgetQuotaState {
    val providerId = this[WidgetQuotaPreferenceKeys.providerId]
    return WidgetQuotaState(
        status = enumValue(WidgetQuotaPreferenceKeys.status, WidgetQuotaStatus.NoAccount),
        providerName = this[WidgetQuotaPreferenceKeys.providerName] ?: DEFAULT_PROVIDER_NAME,
        providerId = providerId,
        localAccountId = this[WidgetQuotaPreferenceKeys.localAccountId],
        accountName = this[WidgetQuotaPreferenceKeys.accountName],
        level = enumValue(WidgetQuotaPreferenceKeys.level, QuotaLevel.Unknown),
        // Default a never-written widget to Home, not the retired provider-selection deep-link.
        clickTarget = enumValue(WidgetQuotaPreferenceKeys.clickTarget, WidgetClickTarget.Home),
        fields = readFields(),
        // A freshly-placed widget has no persisted state yet. Default to the unconfigured guide card
        // (the clean centered "尚未配置" copy) rather than the data layout, and to no-accounts so the
        // hint reads "add an account first" — matching what the app writes once it computes real state,
        // so the first render isn't a different-looking placeholder than the post-open state.
        isUnconfigured = this[WidgetQuotaPreferenceKeys.isUnconfigured] ?: true,
        hasAccounts = this[WidgetQuotaPreferenceKeys.hasAccounts] ?: false,
        // Resolve the brand icon from the provider id at read time — resource ids aren't stable
        // enough to persist across app updates, so we never serialize them.
        lastUpdatedAt = this[WidgetQuotaPreferenceKeys.lastUpdatedAt]?.toInstantOrNull(),
        errorMessageKey = this[WidgetQuotaPreferenceKeys.errorMessageKey],
    )
}

internal fun Preferences.toWidgetQuotaConfiguration(): WidgetQuotaConfiguration =
    WidgetQuotaConfiguration(
        providerId = this[WidgetQuotaPreferenceKeys.configProviderId],
        localAccountId = this[WidgetQuotaPreferenceKeys.configLocalAccountId],
        selectedWindowIds = this[WidgetQuotaPreferenceKeys.configWindowIds].decodeWindowIds(),
    )

internal fun MutablePreferences.writeWidgetQuotaConfiguration(configuration: WidgetQuotaConfiguration) {
    putOrRemove(WidgetQuotaPreferenceKeys.configProviderId, configuration.providerId?.takeIf { it.isNotBlank() })
    putOrRemove(WidgetQuotaPreferenceKeys.configLocalAccountId, configuration.localAccountId?.takeIf { it.isNotBlank() })
    putOrRemove(
        WidgetQuotaPreferenceKeys.configWindowIds,
        configuration.selectedWindowIds.takeIf { it.isNotEmpty() }?.joinToString(WINDOW_ID_SEPARATOR),
    )
}

internal fun MutablePreferences.writeWidgetQuotaState(state: WidgetQuotaState) {
    this[WidgetQuotaPreferenceKeys.status] = state.status.name
    this[WidgetQuotaPreferenceKeys.providerName] = state.providerName
    putOrRemove(WidgetQuotaPreferenceKeys.providerId, state.providerId)
    putOrRemove(WidgetQuotaPreferenceKeys.localAccountId, state.localAccountId)
    putOrRemove(WidgetQuotaPreferenceKeys.accountName, state.accountName)
    this[WidgetQuotaPreferenceKeys.level] = state.level.name
    this[WidgetQuotaPreferenceKeys.clickTarget] = state.clickTarget.name
    this[WidgetQuotaPreferenceKeys.isUnconfigured] = state.isUnconfigured
    this[WidgetQuotaPreferenceKeys.hasAccounts] = state.hasAccounts
    putOrRemove(WidgetQuotaPreferenceKeys.lastUpdatedAt, state.lastUpdatedAt?.toString())
    putOrRemove(WidgetQuotaPreferenceKeys.errorMessageKey, state.errorMessageKey)
    writeFields(state.fields)
}

internal fun MutablePreferences.clearWidgetQuotaStateIfAccountMatches(
    providerId: String,
    localAccountId: String,
): Boolean {
    val currentState = toWidgetQuotaState()
    val currentConfiguration = toWidgetQuotaConfiguration()
    val stateMatches = currentState.providerId == providerId && currentState.localAccountId == localAccountId
    val configurationMatches = currentConfiguration.accountMatches(providerId, localAccountId)
    if (!stateMatches && !configurationMatches) {
        return false
    }
    if (stateMatches) {
        writeWidgetQuotaState(
            currentState.copy(
                status = WidgetQuotaStatus.NoAccount,
                providerId = null,
                localAccountId = null,
                accountName = null,
                level = QuotaLevel.Unknown,
                clickTarget = WidgetClickTarget.Home,
                fields = emptyList(),
                isUnconfigured = true,
                lastUpdatedAt = null,
                errorMessageKey = null,
            ),
        )
    }
    if (configurationMatches) {
        writeWidgetQuotaConfiguration(WidgetQuotaConfiguration())
    }
    return true
}

private const val WINDOW_ID_SEPARATOR = "\n"

private fun String?.decodeWindowIds(): List<String> =
    this?.split(WINDOW_ID_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

private fun Preferences.readFields(): List<WidgetField> {
    val count = (this[WidgetQuotaPreferenceKeys.fieldCount] ?: 0).coerceIn(0, WIDGET_MAX_FIELDS)
    return (0 until count).mapNotNull { i ->
        val windowId = this[WidgetQuotaPreferenceKeys.fieldWindowId(i)] ?: return@mapNotNull null
        WidgetField(
            windowId = windowId,
            isBalance = this[WidgetQuotaPreferenceKeys.fieldIsBalance(i)] ?: false,
            percent = this[WidgetQuotaPreferenceKeys.fieldPercent(i)],
            balanceAmount = this[WidgetQuotaPreferenceKeys.fieldBalanceAmount(i)],
            balanceCurrency = this[WidgetQuotaPreferenceKeys.fieldBalanceCurrency(i)],
            resetAt = this[WidgetQuotaPreferenceKeys.fieldResetAt(i)]?.toInstantOrNull(),
            level = enumValue(WidgetQuotaPreferenceKeys.fieldLevel(i), QuotaLevel.Unknown),
            limitWindowSeconds = this[WidgetQuotaPreferenceKeys.fieldLimitWindowSeconds(i)],
            labelName = this[WidgetQuotaPreferenceKeys.fieldLabelName(i)],
        )
    }
}

private fun MutablePreferences.writeFields(fields: List<WidgetField>) {
    val capped = fields.take(WIDGET_MAX_FIELDS)
    this[WidgetQuotaPreferenceKeys.fieldCount] = capped.size
    for (i in 0 until WIDGET_MAX_FIELDS) {
        val field = capped.getOrNull(i)
        if (field == null) {
            remove(WidgetQuotaPreferenceKeys.fieldWindowId(i))
            remove(WidgetQuotaPreferenceKeys.fieldIsBalance(i))
            remove(WidgetQuotaPreferenceKeys.fieldPercent(i))
            remove(WidgetQuotaPreferenceKeys.fieldBalanceAmount(i))
            remove(WidgetQuotaPreferenceKeys.fieldBalanceCurrency(i))
            remove(WidgetQuotaPreferenceKeys.fieldResetAt(i))
            remove(WidgetQuotaPreferenceKeys.fieldLevel(i))
            remove(WidgetQuotaPreferenceKeys.fieldLimitWindowSeconds(i))
            remove(WidgetQuotaPreferenceKeys.fieldLabelName(i))
        } else {
            this[WidgetQuotaPreferenceKeys.fieldWindowId(i)] = field.windowId
            this[WidgetQuotaPreferenceKeys.fieldIsBalance(i)] = field.isBalance
            putOrRemove(WidgetQuotaPreferenceKeys.fieldPercent(i), field.percent)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldBalanceAmount(i), field.balanceAmount)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldBalanceCurrency(i), field.balanceCurrency)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldResetAt(i), field.resetAt?.toString())
            this[WidgetQuotaPreferenceKeys.fieldLevel(i)] = field.level.name
            putOrRemove(WidgetQuotaPreferenceKeys.fieldLimitWindowSeconds(i), field.limitWindowSeconds)
            putOrRemove(WidgetQuotaPreferenceKeys.fieldLabelName(i), field.labelName)
        }
    }
}

private inline fun <reified T : Enum<T>> Preferences.enumValue(
    key: Preferences.Key<String>,
    defaultValue: T,
): T =
    this[key]?.let { rawValue -> runCatching { enumValueOf<T>(rawValue) }.getOrNull() } ?: defaultValue

private fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value == null) remove(key) else this[key] = value
}

private fun MutablePreferences.putOrRemove(key: Preferences.Key<Int>, value: Int?) {
    if (value == null) remove(key) else this[key] = value
}

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

internal object WidgetQuotaPreferenceKeys {
    val status = stringPreferencesKey("widget_quota_status")
    val providerName = stringPreferencesKey("widget_provider_name")
    val providerId = stringPreferencesKey("widget_provider_id")
    val localAccountId = stringPreferencesKey("widget_local_account_id")
    val accountName = stringPreferencesKey("widget_account_name")
    val level = stringPreferencesKey("widget_level")
    val clickTarget = stringPreferencesKey("widget_click_target")
    val isUnconfigured = booleanPreferencesKey("widget_is_unconfigured")
    val hasAccounts = booleanPreferencesKey("widget_has_accounts")
    val lastUpdatedAt = stringPreferencesKey("widget_last_updated_at")
    val errorMessageKey = stringPreferencesKey("widget_error_message_key")
    val fieldCount = intPreferencesKey("widget_field_count")
    val configProviderId = stringPreferencesKey("widget_config_provider_id")
    val configLocalAccountId = stringPreferencesKey("widget_config_local_account_id")
    val configWindowIds = stringPreferencesKey("widget_config_window_ids")

    fun fieldWindowId(i: Int) = stringPreferencesKey("widget_field_${i}_window_id")
    fun fieldIsBalance(i: Int) = booleanPreferencesKey("widget_field_${i}_is_balance")
    fun fieldPercent(i: Int) = intPreferencesKey("widget_field_${i}_percent")
    fun fieldBalanceAmount(i: Int) = stringPreferencesKey("widget_field_${i}_balance_amount")
    fun fieldBalanceCurrency(i: Int) = stringPreferencesKey("widget_field_${i}_balance_currency")
    fun fieldResetAt(i: Int) = stringPreferencesKey("widget_field_${i}_reset_at")
    fun fieldLevel(i: Int) = stringPreferencesKey("widget_field_${i}_level")
    fun fieldLimitWindowSeconds(i: Int) = intPreferencesKey("widget_field_${i}_limit_window_seconds")
    fun fieldLabelName(i: Int) = stringPreferencesKey("widget_field_${i}_label_name")
}

private const val DEFAULT_PROVIDER_NAME = "AIMeter"
