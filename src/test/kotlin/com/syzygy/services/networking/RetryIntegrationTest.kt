package com.syzygy.services.networking

import com.syzygyhub.foundation.contracts.network.NetworkMethod
import com.syzygyhub.foundation.contracts.network.NetworkRequest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integration tests for [OkHttpNetworkClient] retry behaviour, using
 * [MockWebServer] to simulate server-side failures followed by recovery.
 *
 * The retry loop inside [OkHttpNetworkClient] fires only on transport-level
 * errors (i.e. [IOException] or similar).  HTTP 4xx / 5xx responses are
 * non-retryable by design and always propagate immediately.
 *
 * Timing tests inject [TestBackoffClock] so the real [kotlinx.coroutines.delay]
 * is NOT invoked — the test clock records requested delays without sleeping,
 * keeping the suite fast and making retry counts and delay values directly
 * verifiable.
 */
class RetryIntegrationTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * A [RequestInterceptor] that counts each outbound call.
     */
    private inner class CountingInterceptor : RequestInterceptor {
        var callCount = 0

        override fun intercept(request: NetworkRequest): NetworkRequest {
            callCount++
            return request
        }
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    /**
     * When the server returns success on the first attempt no retry should occur.
     */
    @Test
    fun `no retry on immediate success`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val recorder = TestBackoffClock()
            val interceptor = CountingInterceptor()
            val client =
                OkHttpNetworkClient(
                    maxRetries = 3,
                    interceptors = listOf(interceptor),
                    backoffClock = recorder,
                )
            val response = client.execute(NetworkRequest(server.url("/").toString(), NetworkMethod.GET))
            assertEquals(200, response.statusCode)
            assertEquals(1, interceptor.callCount)
            // No backoff delays should have been requested
            assertEquals(0, recorder.recordedDelays.size)
        }

    /**
     * HTTP 4xx errors are NOT retried — the error propagates after the first attempt.
     */
    @Test
    fun `HTTP 4xx is not retried`() =
        runTest {
            repeat(3) { server.enqueue(MockResponse().setResponseCode(404).setBody("nope")) }
            val recorder = TestBackoffClock()
            val client = OkHttpNetworkClient(maxRetries = 3, backoffClock = recorder)
            assertFailsWith<NetworkError> {
                client.execute(NetworkRequest(server.url("/").toString(), NetworkMethod.GET))
            }
            // Only one request should have been made — 4xx is not retried
            assertEquals(1, server.requestCount)
            // No backoff delays should have been recorded
            assertEquals(0, recorder.recordedDelays.size)
        }

    /**
     * HTTP 5xx errors are NOT retried (they are treated as HTTP-level errors).
     */
    @Test
    fun `HTTP 5xx is not retried`() =
        runTest {
            repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("err")) }
            val recorder = TestBackoffClock()
            val client = OkHttpNetworkClient(maxRetries = 3, backoffClock = recorder)
            val ex =
                assertFailsWith<NetworkError> {
                    client.execute(NetworkRequest(server.url("/").toString(), NetworkMethod.GET))
                }
            assertEquals("server_error", ex.code.rawValue)
            assertEquals(1, server.requestCount)
            assertEquals(0, recorder.recordedDelays.size)
        }

    /**
     * Max retry ceiling: after exhausting all transport-error retries the client
     * throws [NetworkError] with code networkUnavailable, and the backoff clock
     * is invoked exactly [maxRetries] times (once per failed attempt).
     *
     * We simulate transport errors by pointing the client at a port where the
     * server has been shut down.
     */
    @Test
    fun `max retry ceiling enforced — exhausted retries throw NetworkError`() =
        runTest {
            server.shutdown()
            val recorder = TestBackoffClock()
            val maxRetries = 3
            val client = OkHttpNetworkClient(maxRetries = maxRetries, backoffClock = recorder)
            val ex =
                assertFailsWith<NetworkError> {
                    client.execute(NetworkRequest("http://127.0.0.1:${server.port}/", NetworkMethod.GET))
                }
            assertEquals("network_unavailable", ex.code.rawValue)
            assertTrue(ex.message!!.contains(maxRetries.toString()), "Should mention retry count in message")
            // repeat(maxRetries) loops attempt 0..(maxRetries-1); delay is called after each
            // failed attempt (inside the loop body), so delays.size == maxRetries
            assertEquals(maxRetries, recorder.recordedDelays.size)
        }

    /**
     * The backoff clock records correct exponential delay values:
     * attempt 0 → 500 ms, attempt 1 → 1000 ms, attempt 2 → 2000 ms.
     */
    @Test
    fun `records correct exponential delays`() =
        runTest {
            server.shutdown()
            val recorder = TestBackoffClock()
            val client = OkHttpNetworkClient(maxRetries = 3, backoffClock = recorder)
            assertFailsWith<NetworkError> {
                client.execute(NetworkRequest("http://127.0.0.1:${server.port}/", NetworkMethod.GET))
            }
            assertEquals(3, recorder.recordedDelays.size)
            assertEquals(500L, recorder.recordedDelays[0], "attempt 0 delay should be 500 ms")
            assertEquals(1000L, recorder.recordedDelays[1], "attempt 1 delay should be 1000 ms")
            assertEquals(2000L, recorder.recordedDelays[2], "attempt 2 delay should be 2000 ms")
        }

    /**
     * Recovery on Nth attempt: client recovers when the server fails N-1 times
     * then succeeds on attempt N. Verifies that delays are recorded for each
     * failed attempt (N-1 delays) and the final response is successful.
     *
     * Transport-level retries are not triggered by HTTP 5xx — this test uses
     * a socket-close to simulate a transport failure for the first two attempts,
     * then a real 200 on the third.
     *
     * Because MockWebServer cannot easily simulate a mid-stream transport failure
     * mixed with a later success in the same socket lifecycle, we verify the
     * equivalent: the client succeeds on the first attempt when a 200 is waiting,
     * and the TestBackoffClock recorded zero delays (no retry needed), confirming
     * the machinery returns early on the first success — the recovery path is
     * proven by the no-retry-on-immediate-success test together with the
     * max-retry-ceiling test.
     *
     * This test specifically validates the "recovers on Nth attempt" scenario by
     * injecting a TestBackoffClock and confirming that after N-1 recorded delays
     * the client ultimately returns a 200 when the server cooperates on the Nth call.
     */
    @Test
    fun `recovers on Nth attempt after N-1 transport failures`() =
        runTest {
            // Two transport-error responses (socket close) followed by a 200
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(MockResponse().setResponseCode(200).setBody("recovered"))

            val recorder = TestBackoffClock()
            val client = OkHttpNetworkClient(maxRetries = 3, backoffClock = recorder)
            val response = client.execute(NetworkRequest(server.url("/").toString(), NetworkMethod.GET))

            assertEquals(200, response.statusCode)
            // Two transport failures → two delays recorded before success
            assertEquals(2, recorder.recordedDelays.size)
        }
}
