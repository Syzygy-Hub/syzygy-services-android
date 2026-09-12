package com.syzygy.services.remoteconfig

import com.syzygyhub.foundation.contracts.network.NetworkClientProtocol
import com.syzygyhub.foundation.contracts.network.NetworkMethod
import com.syzygyhub.foundation.contracts.network.NetworkRequest
import com.syzygyhub.foundation.primitives.time.SyzygyTimestamp
import java.util.concurrent.ConcurrentHashMap

/**
 * Contract for fetching and reading remote configuration values.
 */
interface RemoteConfigProvider {
    /**
     * The timestamp of the most recent successful fetch, or `null` if
     * no fetch has been performed yet.
     */
    val lastFetchTime: SyzygyTimestamp?

    /**
     * Fetches the latest configuration from the remote source.
     *
     * Implementations must not throw — failures are handled internally and
     * the provider falls back to default values.
     */
    suspend fun fetch()

    /**
     * Returns the [String] value for [key], or [default] when the key is
     * absent or the stored value cannot be cast to [String].
     */
    fun getString(
        key: String,
        default: String = "",
    ): String

    /**
     * Returns the [Int] value for [key], or [default] when the key is absent
     * or cannot be interpreted as an [Int].
     */
    fun getInt(
        key: String,
        default: Int = 0,
    ): Int

    /**
     * Returns the [Boolean] value for [key], or [default] when the key is
     * absent or cannot be interpreted as a [Boolean].
     */
    fun getBoolean(
        key: String,
        default: Boolean = false,
    ): Boolean

    /**
     * Returns the [Double] value for [key], or [default] when the key is
     * absent or cannot be interpreted as a [Double].
     */
    fun getDouble(
        key: String,
        default: Double = 0.0,
    ): Double
}

/**
 * [RemoteConfigProvider] that fetches configuration from a remote JSON
 * endpoint via [networkClient] and falls back to [defaults] on any failure.
 *
 * The remote endpoint is expected to return a flat JSON object whose keys map
 * to string values (e.g. `{"feature_flag":"true","timeout_seconds":"30"}`).
 * Type-coercion from strings is handled by each typed getter.
 *
 * @param networkClient The client used to perform the fetch request.
 * @param configUrl The URL of the remote configuration endpoint.
 * @param defaults Default values returned when a key is absent or the fetch
 *   has not yet succeeded.
 */
class NetworkRemoteConfigProvider(
    private val networkClient: NetworkClientProtocol? = null,
    private val configUrl: String = "",
    private val defaults: Map<String, String> = emptyMap(),
) : RemoteConfigProvider {
    private val store = ConcurrentHashMap<String, String>(defaults)

    /** Timestamp of the most recent successful remote fetch. */
    override var lastFetchTime: SyzygyTimestamp? = null
        private set

    /**
     * Fetches the remote configuration. On failure the existing (default)
     * values are retained and [lastFetchTime] is not updated.
     */
    override suspend fun fetch() {
        if (networkClient == null || configUrl.isBlank()) return
        runCatching {
            val response =
                networkClient!!.execute(
                    NetworkRequest(
                        url = configUrl,
                        method = NetworkMethod.GET,
                    ),
                )
            if (response.isSuccess) {
                parseJsonFlat(response.data.decodeToString()).forEach { (k, v) -> store[k] = v }
                lastFetchTime = SyzygyTimestamp.now()
            }
        }
    }

    /** Returns the stored [String] value for [key], or [default]. */
    override fun getString(
        key: String,
        default: String,
    ): String = store[key] ?: default

    /** Returns the stored value for [key] coerced to [Int], or [default]. */
    override fun getInt(
        key: String,
        default: Int,
    ): Int = store[key]?.toIntOrNull() ?: default

    /** Returns the stored value for [key] coerced to [Boolean], or [default]. */
    override fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean = store[key]?.toBooleanStrictOrNull() ?: store[key]?.let { it == "1" } ?: default

    /** Returns the stored value for [key] coerced to [Double], or [default]. */
    override fun getDouble(
        key: String,
        default: Double,
    ): Double = store[key]?.toDoubleOrNull() ?: default

    /**
     * Sets a value directly — useful for tests or in-process overrides.
     *
     * @param key The configuration key.
     * @param value The string representation of the value.
     */
    fun setValue(
        key: String,
        value: String,
    ) {
        store[key] = value
    }

    // ------------------------------------------------------------------
    // Minimal JSON flat-object parser (avoids adding a JSON library)
    // ------------------------------------------------------------------

    /** Parses a flat `{"key":"value",...}` JSON string into a [Map]. */
    private fun parseJsonFlat(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pattern = Regex(""""([^"]+)"\s*:\s*"([^"]*)"""")
        pattern.findAll(json).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }
}
