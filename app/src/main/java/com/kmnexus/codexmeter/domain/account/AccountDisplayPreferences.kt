package com.kmnexus.codexmeter.domain.account

import com.kmnexus.codexmeter.domain.model.LocalAccountId

/**
 * How the user arranged their accounts: the order they chose and the ones they hid from Home and
 * the notification. Every saved account is equal; there is no "current" one.
 */
data class AccountDisplayPreferences(
    val order: List<LocalAccountId> = emptyList(),
    val hidden: Set<LocalAccountId> = emptySet(),
) {
    /**
     * [items] in the chosen order. Accounts the order does not mention yet (added later) follow the
     * known ones in their saved order; entries for removed accounts are ignored.
     */
    fun <T> arrange(items: List<T>, idOf: (T) -> LocalAccountId): List<T> {
        val position = order.withIndex().associate { (index, id) -> id to index }
        return items.withIndex()
            .sortedWith(compareBy({ position[idOf(it.value)] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    fun <T> visible(items: List<T>, idOf: (T) -> LocalAccountId): List<T> =
        arrange(items, idOf).filterNot { isHidden(idOf(it)) }

    fun isHidden(id: LocalAccountId): Boolean = id in hidden

    fun withHidden(id: LocalAccountId, hidden: Boolean): AccountDisplayPreferences =
        copy(hidden = if (hidden) this.hidden + id else this.hidden - id)

    /**
     * Moves [id] by [offset] places within [currentOrder] (the accounts as arranged now) and stores
     * that whole order. Moving past either end, or an unknown id, changes nothing.
     */
    fun moved(id: LocalAccountId, offset: Int, currentOrder: List<LocalAccountId>): AccountDisplayPreferences {
        val from = currentOrder.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in currentOrder.indices || offset == 0) return this
        val reordered = currentOrder.toMutableList().apply { add(to, removeAt(from)) }
        return copy(order = reordered)
    }

    fun without(id: LocalAccountId): AccountDisplayPreferences =
        copy(order = order - id, hidden = hidden - id)
}

interface AccountDisplayPreferenceReader {
    suspend fun accountDisplayPreferences(): AccountDisplayPreferences
}

interface AccountDisplayPreferenceStore : AccountDisplayPreferenceReader {
    /** Applies [transform] atomically and returns the stored result. */
    suspend fun updateAccountDisplayPreferences(
        transform: (AccountDisplayPreferences) -> AccountDisplayPreferences,
    ): AccountDisplayPreferences
}

class InMemoryAccountDisplayPreferenceStore(
    private var preferences: AccountDisplayPreferences = AccountDisplayPreferences(),
) : AccountDisplayPreferenceStore {
    override suspend fun accountDisplayPreferences(): AccountDisplayPreferences = preferences

    override suspend fun updateAccountDisplayPreferences(
        transform: (AccountDisplayPreferences) -> AccountDisplayPreferences,
    ): AccountDisplayPreferences = transform(preferences).also { preferences = it }
}
