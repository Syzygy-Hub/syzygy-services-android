package com.syzygy.services.persistence

/**
 * Defines the contract for key-value persistence storage.
 */
interface StorageProvider {
    /** Stores [value] for the given [key]. */
    fun set(
        key: String,
        value: String,
    )

    /** Retrieves the value for the given [key], or null if not set. */
    fun get(key: String): String?

    /** Removes the value for the given [key]. */
    fun remove(key: String)
}

/**
 * A [StorageProvider] backed by an in-memory map,
 * simulating SharedPreferences behaviour for use in tests and stubs.
 */
class InMemoryStorageProvider : StorageProvider {
    private val store = mutableMapOf<String, String>()

    override fun set(
        key: String,
        value: String,
    ) {
        store[key] = value
    }

    override fun get(key: String): String? = store[key]

    override fun remove(key: String) {
        store.remove(key)
    }
}
