package com.kmnexus.codexmeter.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal fun Preferences.hasAccountsWidgetState(): Boolean =
    this[AccountsWidgetPreferenceKeys.hasAccounts] != null

internal fun Preferences.toAccountsWidgetState(): AccountsWidgetState =
    AccountsWidgetState(
        // Rows written by another app version may not decode; then the widget shows no rows until the
        // next refresh rewrites them, instead of failing to draw.
        rows = this[AccountsWidgetPreferenceKeys.rows]
            ?.let { encoded -> runCatching { rowsJson.decodeFromString(rowsSerializer, encoded) }.getOrNull() }
            .orEmpty(),
        hasAccounts = this[AccountsWidgetPreferenceKeys.hasAccounts] ?: false,
    )

internal fun MutablePreferences.writeAccountsWidgetState(state: AccountsWidgetState) {
    this[AccountsWidgetPreferenceKeys.rows] = rowsJson.encodeToString(rowsSerializer, state.rows)
    this[AccountsWidgetPreferenceKeys.hasAccounts] = state.hasAccounts
}

internal fun Preferences.toAccountsWidgetConfiguration(): AccountsWidgetConfiguration =
    AccountsWidgetConfiguration(
        chosenAccountIds = this[AccountsWidgetPreferenceKeys.chosenAccountIds]
            ?.split(ACCOUNT_ID_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty(),
    )

internal fun MutablePreferences.writeAccountsWidgetConfiguration(configuration: AccountsWidgetConfiguration) {
    if (configuration.followsVisibleAccounts) {
        remove(AccountsWidgetPreferenceKeys.chosenAccountIds)
    } else {
        this[AccountsWidgetPreferenceKeys.chosenAccountIds] = configuration.chosenAccountIds.joinToString(ACCOUNT_ID_SEPARATOR)
    }
}

private val rowsJson = Json { ignoreUnknownKeys = true }
private val rowsSerializer = ListSerializer(AccountsWidgetRow.serializer())
private const val ACCOUNT_ID_SEPARATOR = "\n"

internal object AccountsWidgetPreferenceKeys {
    val rows = stringPreferencesKey("accounts_widget_rows")
    val hasAccounts = booleanPreferencesKey("accounts_widget_has_accounts")
    val chosenAccountIds = stringPreferencesKey("accounts_widget_config_account_ids")
}
