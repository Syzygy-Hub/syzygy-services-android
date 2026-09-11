package com.syzygy.services.networking

/**
 * Defines the contract for performing HTTP network requests.
 */
interface NetworkClient {
    /** Performs a GET request to [url] and returns the response body as a String. */
    suspend fun get(url: String): String

    /** Performs a POST request to [url] with [body] and returns the response body. */
    suspend fun post(
        url: String,
        body: String,
    ): String
}

/**
 * A [NetworkClient] backed by coroutine-based HTTP calls.
 * Replace the stub implementation with OkHttp or Ktor in production.
 */
class CoroutineNetworkClient : NetworkClient {
    override suspend fun get(url: String): String {
        // Stub — replace with real HTTP GET implementation.
        return ""
    }

    override suspend fun post(
        url: String,
        body: String,
    ): String {
        // Stub — replace with real HTTP POST implementation.
        return ""
    }
}
