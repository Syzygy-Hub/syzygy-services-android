package com.syzygy.services.remoteconfig

/**
 * Defines the contract for fetching remote configuration values.
 */
interface RemoteConfigProvider {
    /** Returns the string value for [key], or null if not set. */
    fun getString(key: String): String?

    /** Returns the boolean value for [key], or null if not set. */
    fun getBoolean(key: String): Boolean?

    /** Sets a value for [key] (used in testing and in-memory implementations). */
    fun setValue(
        key: String,
        value: Any?,
    )
}

/**
 * A [RemoteConfigProvider] backed by an in-memory map.
 */
class InMemoryRemoteConfigProvider(initialValues: Map<String, Any> = emptyMap()) : RemoteConfigProvider {
    private val store: MutableMap<String, Any?> = initialValues.toMutableMap()

    override fun getString(key: String): String? = store[key] as? String

    override fun getBoolean(key: String): Boolean? = store[key] as? Boolean

    override fun setValue(
        key: String,
        value: Any?,
    ) {
        store[key] = value
    }
}
