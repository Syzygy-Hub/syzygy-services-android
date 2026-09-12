package com.syzygy.services.networking

import com.syzygyhub.foundation.contracts.network.NetworkClientProtocol
import com.syzygyhub.foundation.contracts.network.NetworkMethod
import com.syzygyhub.foundation.contracts.network.NetworkRequest
import com.syzygyhub.foundation.contracts.network.NetworkResponse
import com.syzygyhub.foundation.errors.SyzygyError
import com.syzygyhub.foundation.errors.SyzygyErrorCode
import com.syzygyhub.foundation.errors.SyzygyErrorSeverity
import kotlinx.coroutines.delay
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
) : NetworkClientProtocol {
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeouts.connectSeconds, TimeUnit.SECONDS)
            .readTimeout(timeouts.readSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeouts.writeSeconds, TimeUnit.SECONDS)
            .build()

    /**
     * Executes [request] after passing it through all interceptors, retrying
     * on transport failures with exponential back-off.
     *
     * @throws NetworkError on HTTP 4xx/5xx or transport failure after all retries.
     */
    override suspend fun execute(request: NetworkRequest): NetworkResponse {
        val intercepted = interceptors.fold(request) { acc, interceptor -> interceptor.intercept(acc) }
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                return executeOnce(intercepted)
            } catch (e: NetworkError) {
                // Do not retry HTTP-level errors (4xx/5xx) — only transport errors
                throw e
            } catch (e: Throwable) {
                lastError = e
                val backoffMs = (2.0.pow(attempt) * 500).toLong()
                delay(backoffMs)
            }
        }
        throw NetworkError(
            SyzygyErrorCode.networkUnavailable,
            "Request failed after $maxRetries retries: ${lastError?.message}",
            SyzygyErrorSeverity.ERROR,
            lastError,
        )
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

/** Integer power helper to avoid kotlin-math dependency. */
private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}
