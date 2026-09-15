package com.syzygy.services.networking

import com.syzygyhub.foundation.contracts.logging.LoggerProtocol
import com.syzygyhub.foundation.contracts.network.NetworkClientProtocol
import com.syzygyhub.foundation.contracts.network.NetworkMethod
import com.syzygyhub.foundation.contracts.network.NetworkRequest
import com.syzygyhub.foundation.contracts.network.NetworkResponse
import com.syzygyhub.foundation.errors.SyzygyError
import com.syzygyhub.foundation.errors.SyzygyErrorCode
import com.syzygyhub.foundation.errors.SyzygyErrorSeverity
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Intercepts outbound [NetworkRequest]s before execution, allowing mutation of
 * headers, URLs, or bodies (e.g. for auth token injection or logging).
 */
interface RequestInterceptor {
    /**
     * Called just before [request] is sent. Return the (possibly mutated)
     * request that should actually be executed.
     */
    fun intercept(request: NetworkRequest): NetworkRequest
}

/**
 * Timeout configuration for [OkHttpNetworkClient].
 *
 * @property connectSeconds Maximum seconds to wait for a TCP connection.
 * @property readSeconds Maximum seconds to wait for a response read.
 * @property writeSeconds Maximum seconds to wait for a request body write.
 */
data class TimeoutConfiguration(
    val connectSeconds: Long = 30,
    val readSeconds: Long = 30,
    val writeSeconds: Long = 30,
)

/**
 * A concrete [SyzygyError] produced by networking failures.
 *
 * @property code Machine-readable error code.
 * @property message Human-readable description.
 * @property severity The operational impact.
 * @property underlyingError The lower-level exception that caused this error.
 */
class NetworkError(
    override val code: SyzygyErrorCode,
    override val message: String,
    override val severity: SyzygyErrorSeverity = SyzygyErrorSeverity.ERROR,
    override val underlyingError: Throwable? = null,
) : Exception(message), SyzygyError

/** Maps an HTTP status code to a [NetworkError]. */
internal fun httpStatusToError(
    statusCode: Int,
    body: String,
): NetworkError =
    when (statusCode) {
        401 ->
            NetworkError(
                SyzygyErrorCode.unauthenticated,
                "HTTP 401 Unauthenticated: $body",
                SyzygyErrorSeverity.ERROR,
            )
        403 ->
            NetworkError(
                SyzygyErrorCode.forbidden,
                "HTTP 403 Forbidden: $body",
                SyzygyErrorSeverity.ERROR,
            )
        404 ->
            NetworkError(
                SyzygyErrorCode.notFound,
                "HTTP 404 Not Found: $body",
                SyzygyErrorSeverity.ERROR,
            )
        in 400..499 ->
            NetworkError(
                SyzygyErrorCode.unknown,
                "HTTP $statusCode Client Error: $body",
                SyzygyErrorSeverity.ERROR,
            )
        in 500..599 ->
            NetworkError(
                SyzygyErrorCode.serverError,
                "HTTP $statusCode Server Error: $body",
                SyzygyErrorSeverity.ERROR,
            )
        else ->
            NetworkError(
                SyzygyErrorCode.unknown,
                "HTTP $statusCode Unexpected: $body",
                SyzygyErrorSeverity.ERROR,
            )
    }

/**
 * OkHttp-backed implementation of [NetworkClientProtocol].
 *
 * Supports GET, POST, PUT, DELETE, and PATCH via [execute], with
 * convenience wrappers for each method. Requests pass through all registered
 * [interceptors] before being sent and are retried up to [maxRetries] times
 * with exponential back-off on transport-level failures.
 *
 * @param timeouts Timeout settings applied to the underlying [OkHttpClient].
 * @param interceptors List of [RequestInterceptor]s applied in order.
 * @param maxRetries Maximum number of retry attempts on transport errors.
 */
class OkHttpNetworkClient(
    timeouts: TimeoutConfiguration = TimeoutConfiguration(),
    private val interceptors: List<RequestInterceptor> = emptyList(),
    private val maxRetries: Int = 3,
    private val logger: LoggerProtocol? = null,
    private val backoffClock: BackoffClock = ExponentialBackoffClock(),
) : NetworkClientProtocol, AutoCloseable {
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeouts.connectSeconds, TimeUnit.SECONDS)
            .readTimeout(timeouts.readSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeouts.writeSeconds, TimeUnit.SECONDS)
            .build()

    /** Whether this client has been closed. */
    @Volatile
    private var closed = false

    /**
     * Releases the underlying [OkHttpClient] dispatcher and connection pool.
     *
     * After calling [close], any further [execute] call throws [IllegalStateException].
     */
    override fun close() {
        closed = true
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * Alias for [close] — releases all resources held by this client.
     *
     * Provided for callers following the cross-platform Syzygy dispose convention.
     *
     * @see close
     */
    fun dispose() = close()

    /**
     * Executes [request] after passing it through all interceptors, retrying
     * on transport failures with exponential back-off.
     *
     * @throws IllegalStateException when this client has been [close]d.
     * @throws NetworkError on HTTP 4xx/5xx or transport failure after all retries.
     */
    override suspend fun execute(request: NetworkRequest): NetworkResponse {
        check(!closed) { "OkHttpNetworkClient has been closed" }
        val intercepted = interceptors.fold(request) { acc, interceptor -> interceptor.intercept(acc) }

        // Log request (headers minus Authorization)
        logger?.debug(
            "NetworkClient request: ${intercepted.method.value} ${intercepted.url}",
            buildMap {
                put("method", intercepted.method.value)
                put("url", intercepted.url)
                put("body_size", (intercepted.body?.size ?: 0).toString())
                intercepted.headers
                    .filterKeys { it.lowercase() != "authorization" }
                    .forEach { (k, v) -> put("header.$k", v) }
            },
        )

        val startMs = System.currentTimeMillis()
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                val response = executeOnce(intercepted)
                val elapsedMs = System.currentTimeMillis() - startMs
                logger?.debug(
                    "NetworkClient response: ${response.statusCode} ${intercepted.url}",
                    mapOf(
                        "status_code" to response.statusCode.toString(),
                        "elapsed_ms" to elapsedMs.toString(),
                        "body_size" to response.data.size.toString(),
                    ),
                )
                return response
            } catch (e: NetworkError) {
                val elapsedMs = System.currentTimeMillis() - startMs
                logger?.error(
                    "NetworkClient error: ${e.code.rawValue} ${intercepted.url}",
                    e,
                    mapOf(
                        "status_code" to e.code.rawValue,
                        "elapsed_ms" to elapsedMs.toString(),
                    ),
                )
                // Do not retry HTTP-level errors (4xx/5xx) — only transport errors
                throw e
            } catch (e: Throwable) {
                lastError = e
                logger?.warning(
                    "NetworkClient transport error (attempt ${attempt + 1}/$maxRetries): ${e.message}",
                    mapOf("url" to intercepted.url, "attempt" to (attempt + 1).toString()),
                )
                backoffClock.delay(attempt)
            }
        }
        val finalError =
            NetworkError(
                SyzygyErrorCode.networkUnavailable,
                "Request failed after $maxRetries retries: ${lastError?.message}",
                SyzygyErrorSeverity.ERROR,
                lastError,
            )
        logger?.error(
            "NetworkClient gave up after $maxRetries retries: ${intercepted.url}",
            finalError,
            mapOf("url" to intercepted.url),
        )
        throw finalError
    }

    /** Convenience wrapper for GET requests. */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = execute(NetworkRequest(url, NetworkMethod.GET, headers))

    /** Convenience wrapper for POST requests. */
    suspend fun post(
        url: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = execute(NetworkRequest(url, NetworkMethod.POST, headers, body))

    /** Convenience wrapper for PUT requests. */
    suspend fun put(
        url: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = execute(NetworkRequest(url, NetworkMethod.PUT, headers, body))

    /** Convenience wrapper for DELETE requests. */
    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = execute(NetworkRequest(url, NetworkMethod.DELETE, headers))

    /** Convenience wrapper for PATCH requests. */
    suspend fun patch(
        url: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ): NetworkResponse = execute(NetworkRequest(url, NetworkMethod.PATCH, headers, body))

    private suspend fun executeOnce(request: NetworkRequest): NetworkResponse =
        suspendCancellableCoroutine { cont ->
            val body =
                request.body?.let {
                    val contentType =
                        (request.headers["Content-Type"] ?: "application/json").toMediaTypeOrNull()
                    it.toRequestBody(contentType)
                }

            val okRequest =
                Request.Builder()
                    .url(request.url)
                    .method(request.method.value, body)
                    .apply { request.headers.forEach { (k, v) -> addHeader(k, v) } }
                    .build()

            val call = okHttpClient.newCall(okRequest)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        response.use { resp ->
                            val responseBody = resp.body?.bytes() ?: ByteArray(0)
                            val headers = resp.headers.toMap()
                            val networkResponse =
                                NetworkResponse(
                                    statusCode = resp.code,
                                    data = responseBody,
                                    headers = headers,
                                )
                            if (!networkResponse.isSuccess) {
                                cont.resumeWithException(
                                    httpStatusToError(resp.code, responseBody.decodeToString()),
                                )
                            } else {
                                cont.resume(networkResponse)
                            }
                        }
                    }
                },
            )
        }
}
