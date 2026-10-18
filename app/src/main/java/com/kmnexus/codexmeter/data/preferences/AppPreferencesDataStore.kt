package com.kmnexus.codexmeter.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File

/**
 * The preferences file shared by retention, notification, quota-window, account-display and currency
 * preferences.
 */
object AppPreferencesDataStore {
    fun create(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(DropCurrentAccountSelection),
            produceFile = { file },
        )
}

/**
 * Up to 1.1.0 the file also held a "current account" (a provider and a local account id). Nothing
 * reads it any more, so the first read after updating removes it, together with an id that may
 * still name an account disconnected since.
 */
internal object DropCurrentAccountSelection : DataMigration<Preferences> {
    private val keys = listOf(
        stringPreferencesKey("current_provider_id"),
        stringPreferencesKey("current_local_account_id"),
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = keys.any { it in currentData }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { keys.forEach { remove(it) } }.toPreferences()

    override suspend fun cleanUp() = Unit
}
