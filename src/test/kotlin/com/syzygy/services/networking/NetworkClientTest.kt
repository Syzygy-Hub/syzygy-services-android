package com.syzygy.services.networking

import com.syzygyhub.foundation.contracts.logging.LogEntry
import com.syzygyhub.foundation.contracts.logging.LoggerProtocol
import com.syzygyhub.foundation.contracts.network.NetworkRequest
import com.syzygyhub.foundation.contracts.network.NetworkResponse
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Minimal in-test logger that accumulates all [LogEntry] records. */
private class CapturingLogger : LoggerProtocol {
    val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry) {
        entries.add(entry)
    }
}

class NetworkClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpNetworkClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpNetworkClient(maxRetries = 1)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful GET returns 200 response with body`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
            val response = client.get(server.url("/test").toString())
            assertEquals(200, response.statusCode)
            assertEquals("hello", response.data.decodeToString())
            assertTrue(response.isSuccess)
        }

    @Test
    fun `404 response throws NetworkError with notFound code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
            val ex =
                assertFailsWith<NetworkError> {
                    client.get(server.url("/missing").toString())
                }
            assertEquals("not_found", ex.code.rawValue)
        }

    @Test
    fun `500 response throws NetworkError with serverError code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
            val ex =
                assertFailsWith<NetworkError> {
                    client.get(server.url("/crash").toString())
                }
            assertEquals("server_error", ex.code.rawValue)
        }

    @Test
    fun `401 response throws NetworkError with unauthenticated code`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401))
            val ex =
                assertFailsWith<NetworkError> {
                    client.get(server.url("/secure").toString())
                }
            assertEquals("unauthenticated", ex.code.rawValue)
        }

    @Test
    fun `RequestInterceptor is invoked and can mutate headers`() =
        runTest {
            val interceptor =
                object : RequestInterceptor {
                    var wasCalled = false

                    override fun intercept(request: NetworkRequest): NetworkRequest {
                        wasCalled = true
                        return request.copy(headers = request.headers + mapOf("X-Test" to "1"))
                    }
                }
            val clientWithInterceptor = OkHttpNetworkClient(interceptors = listOf(interceptor), maxRetries = 1)
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            clientWithInterceptor.get(server.url("/").toString())
            assertTrue(interceptor.wasCalled, "Interceptor should have been called")
            val recorded = server.takeRequest()
            assertEquals("1", recorded.getHeader("X-Test"))
        }

    @Test
    fun `POST sends body and returns response`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody("created"))
            val response = client.post(server.url("/items").toString(), """{"name":"test"}""".toByteArray())
            assertEquals(201, response.statusCode)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertNotNull(recorded.body)
        }

    @Test
    fun `NetworkResponse isClientError is true for 4xx`() {
        val response = NetworkResponse(400, ByteArray(0), emptyMap())
        assertTrue(response.isClientError)
    }

    @Test
    fun `NetworkResponse isServerError is true for 5xx`() {
        val response = NetworkResponse(503, ByteArray(0), emptyMap())
        assertTrue(response.isServerError)
    }

    // ------------------------------------------------------------------
    // ITEM 3 — Logger tests
    // ------------------------------------------------------------------

    @Test
    fun `logger receives request and response log entries on success`() =
        runTest {
            val logger = CapturingLogger()
            val loggedClient = OkHttpNetworkClient(logger = logger, maxRetries = 1)
            server.enqueue(MockResponse().setResponseCode(200).setBody("logged"))
            loggedClient.get(server.url("/log-test").toString())
            assertTrue(logger.entries.isNotEmpty(), "Logger should have recorded entries")
            val messages = logger.entries.map { it.message }
            assertTrue(messages.any { it.contains("request") }, "Should have a request log entry")
            assertTrue(messages.any { it.contains("response") }, "Should have a response log entry")
        }

    @Test
    fun `logger does not include Authorization header value in request log`() =
        runTest {
            val logger = CapturingLogger()
            val loggedClient = OkHttpNetworkClient(logger = logger, maxRetries = 1)
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            loggedClient.get(
                server.url("/secure").toString(),
                headers = mapOf("Authorization" to "Bearer secret-token"),
            )
            val requestEntries = logger.entries.filter { it.message.contains("request") }
            assertTrue(requestEntries.isNotEmpty())
            val allMetadata = requestEntries.flatMap { it.metadata.values }
            assertTrue(
                allMetadata.none { it.contains("secret-token") },
                "Authorization header value must not appear in log metadata",
            )
        }

    @Test
    fun `logger receives error entry on HTTP error`() =
        runTest {
            val logger = CapturingLogger()
            val loggedClient = OkHttpNetworkClient(logger = logger, maxRetries = 1)
            server.enqueue(MockResponse().setResponseCode(500).setBody("fail"))
            assertFailsWith<NetworkError> {
                loggedClient.get(server.url("/fail").toString())
            }
            assertTrue(logger.entries.isNotEmpty())
            assertTrue(logger.entries.any { it.error != null || it.message.contains("error") })
        }

    @Test
    fun `null logger produces no overhead — client works without logger`() =
        runTest {
            val noLogClient = OkHttpNetworkClient(logger = null, maxRetries = 1)
            server.enqueue(MockResponse().setResponseCode(200).setBody("silent"))
            val response = noLogClient.get(server.url("/silent").toString())
            assertEquals(200, response.statusCode)
        }

    // ------------------------------------------------------------------
    // ITEM 7 — close() tests for OkHttpNetworkClient
    // ------------------------------------------------------------------

    @Test
    fun `close() causes further execute calls to throw IllegalStateException`() =
        runTest {
            val closeable = OkHttpNetworkClient(maxRetries = 1)
            closeable.close()
            assertFailsWith<IllegalStateException> {
                closeable.get(server.url("/after-close").toString())
            }
        }

    // ------------------------------------------------------------------
    // dispose() alias tests (CODE FIX 2)
    // ------------------------------------------------------------------

    @Test
    fun `dispose() causes further execute calls to throw IllegalStateException`() =
        runTest {
            val client = OkHttpNetworkClient(maxRetries = 1)
            client.dispose()
            assertFailsWith<IllegalStateException> {
                client.get(server.url("/after-dispose").toString())
            }
        }

    @Test
    fun `dispose() is idempotent — calling twice does not throw`() {
        val client = OkHttpNetworkClient(maxRetries = 1)
        client.dispose()
        client.dispose() // should not throw
    }
}
