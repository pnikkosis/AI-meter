package com.kmnexus.codexmeter.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferenceStore
import com.kmnexus.codexmeter.domain.account.AccountDisplayPreferences
import com.kmnexus.codexmeter.domain.model.LocalAccountId
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Account order and visibility, kept next to the other app preferences. Holds ids only. */
class AccountDisplayPreferencesDataStore(
    private val dataStore: DataStore<Preferences>,
) : AccountDisplayPreferenceStore {
    override suspend fun accountDisplayPreferences(): AccountDisplayPreferences =
        dataStore.data.first().toAccountDisplayPreferences()

    override suspend fun updateAccountDisplayPreferences(
        transform: (AccountDisplayPreferences) -> AccountDisplayPreferences,
    ): AccountDisplayPreferences {
        var updated = AccountDisplayPreferences()
        dataStore.edit { stored ->
            updated = transform(stored.toAccountDisplayPreferences())
            stored.write(updated)
        }
        return updated
    }

    object Keys {
        val ORDER = stringPreferencesKey("account_display_order")
        val HIDDEN = stringSetPreferencesKey("account_display_hidden")
    }
}

private val orderSerializer = ListSerializer(String.serializer())

private fun Preferences.toAccountDisplayPreferences(): AccountDisplayPreferences =
    AccountDisplayPreferences(
        order = this[AccountDisplayPreferencesDataStore.Keys.ORDER]?.let(::decodeOrder).orEmpty(),
        hidden = this[AccountDisplayPreferencesDataStore.Keys.HIDDEN].orEmpty()
            .filter { it.isNotBlank() }
            .mapTo(mutableSetOf(), ::LocalAccountId),
    )

private fun MutablePreferences.write(preferences: AccountDisplayPreferences) {
    if (preferences.order.isEmpty()) {
        remove(AccountDisplayPreferencesDataStore.Keys.ORDER)
    } else {
        this[AccountDisplayPreferencesDataStore.Keys.ORDER] =
            Json.encodeToString(orderSerializer, preferences.order.map { it.value })
    }
    if (preferences.hidden.isEmpty()) {
        remove(AccountDisplayPreferencesDataStore.Keys.HIDDEN)
    } else {
        this[AccountDisplayPreferencesDataStore.Keys.HIDDEN] = preferences.hidden.mapTo(mutableSetOf()) { it.value }
    }
}

// A value this version cannot read only loses the custom order; accounts still all show.
private fun decodeOrder(raw: String): List<LocalAccountId> =
    runCatching { Json.decodeFromString(orderSerializer, raw) }
        .getOrDefault(emptyList())
        .filter { it.isNotBlank() }
        .distinct()
        .map(::LocalAccountId)
