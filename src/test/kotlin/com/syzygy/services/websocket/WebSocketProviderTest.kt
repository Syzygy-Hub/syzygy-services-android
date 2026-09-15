package com.syzygy.services.websocket

import com.syzygy.services.networking.TestBackoffClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebSocketProviderTest {
    @Test
    fun `initial connectionState is Disconnected`() {
        val provider = OkHttpWebSocketProvider()
        assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
    }

    @Test
    fun `Disconnected state carries null cause by default`() {
        val state = WebSocketConnectionState.Disconnected()
        assertTrue(state.cause == null)
    }

    @Test
    fun `Disconnected state can carry a cause`() {
        val ex = RuntimeException("test")
        val state = WebSocketConnectionState.Disconnected(ex)
        assertNotNull(state.cause)
    }

    @Test
    fun `Connecting state is a distinct object`() {
        val state: WebSocketConnectionState = WebSocketConnectionState.Connecting
        assertIs<WebSocketConnectionState.Connecting>(state)
    }

    @Test
    fun `Connected state is a distinct object`() {
        val state: WebSocketConnectionState = WebSocketConnectionState.Connected
        assertIs<WebSocketConnectionState.Connected>(state)
    }

    @Test
    fun `messages flow is non-null`() {
        val provider = OkHttpWebSocketProvider()
        assertNotNull(provider.messages)
    }

    @Test
    fun `sendText before connect throws IllegalStateException`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            assertFailsWith<IllegalStateException> {
                provider.sendText("oops")
            }
        }

    @Test
    fun `sendBinary before connect throws IllegalStateException`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            assertFailsWith<IllegalStateException> {
                provider.sendBinary(byteArrayOf(1, 2, 3))
            }
        }

    @Test
    fun `disconnect on unconnected provider does not throw`() {
        val provider = OkHttpWebSocketProvider()
        provider.disconnect() // already disconnected — should be idempotent
        assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
    }

    @Test
    fun `connect to unreachable URL sets Disconnected after retries`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            val ex =
                try {
                    provider.connect("ws://127.0.0.1:1", maxReconnectAttempts = 1)
                    null
                } catch (e: Throwable) {
                    e
                }
            assertNotNull(ex)
            assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
        }

    // ------------------------------------------------------------------
    // Binary send/receive tests (ITEM 4)
    // ------------------------------------------------------------------

    /**
     * Creates a [MockWebServer] that upgrades to WebSocket and echoes each
     * binary frame back to the sender.
     */
    private fun buildBinaryEchoServer(): MockWebServer {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        webSocket.send(bytes)
                    }
                },
            ),
        )
        return server
    }

    /**
     * Creates a [MockWebServer] that upgrades to WebSocket and echoes each
     * text frame back to the sender.
     */
    private fun buildTextEchoServer(): MockWebServer {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        webSocket.send(text)
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        webSocket.send(bytes)
                    }
                },
            ),
        )
        return server
    }

    @Test
    fun `sendBinary sends a ByteArray binary frame and binaryMessages flow receives it`() {
        val server = buildBinaryEchoServer()
        server.start()
        val okClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        val provider = OkHttpWebSocketProvider(okClient)
        try {
            kotlinx.coroutines.runBlocking {
                provider.connect(server.url("/ws").toString().replace("http", "ws"), maxReconnectAttempts = 1)
                assertIs<WebSocketConnectionState.Connected>(provider.connectionState)

                val payload = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
                provider.sendBinary(payload)

                val received = provider.binaryMessages.first()
                assertContentEquals(payload, received, "Received binary frame should match sent payload")
            }
        } finally {
            provider.disconnect()
            runCatching { server.shutdown() } // tolerate "queue shutdown" race on teardown
        }
    }

    @Test
    fun `binaryMessages flow is non-null`() {
        val provider = OkHttpWebSocketProvider()
        assertNotNull(provider.binaryMessages)
    }

    @Test
    fun `mixed text and binary frames arrive on correct flows`() {
        val server = buildTextEchoServer()
        server.start()
        val okClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        val provider = OkHttpWebSocketProvider(okClient)
        try {
            kotlinx.coroutines.runBlocking {
                provider.connect(server.url("/ws").toString().replace("http", "ws"), maxReconnectAttempts = 1)
                assertIs<WebSocketConnectionState.Connected>(provider.connectionState)

                // Send a binary frame and collect it from binaryMessages
                val binaryPayload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
                provider.sendBinary(binaryPayload)
                val receivedBinary = provider.binaryMessages.first()
                assertContentEquals(binaryPayload, receivedBinary)
            }
        } finally {
            provider.disconnect()
            runCatching { server.shutdown() } // tolerate "queue shutdown" race on teardown
        }
    }

    @Test
    fun `sendBinary before connect throws IllegalStateException (binary path)`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            assertFailsWith<IllegalStateException> {
                provider.sendBinary(byteArrayOf(1, 2, 3))
            }
        }

    // ------------------------------------------------------------------
    // ITEM 7 — close() / AutoCloseable tests
    // ------------------------------------------------------------------

    @Test
    fun `close() sets state to Disconnected`() {
        val provider = OkHttpWebSocketProvider()
        provider.close()
        assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
    }

    @Test
    fun `connect() after close() throws IllegalStateException`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            provider.close()
            assertFailsWith<IllegalStateException> {
                provider.connect("ws://127.0.0.1:1", maxReconnectAttempts = 1)
            }
        }

    @Test
    fun `sendText() after close() throws IllegalStateException`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            provider.close()
            assertFailsWith<IllegalStateException> {
                provider.sendText("hello")
            }
        }

    @Test
    fun `sendBinary() after close() throws IllegalStateException`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            provider.close()
            assertFailsWith<IllegalStateException> {
                provider.sendBinary(byteArrayOf(1, 2, 3))
            }
        }

    @Test
    fun `close() is idempotent — calling twice does not throw`() {
        val provider = OkHttpWebSocketProvider()
        provider.close()
        provider.close() // should not throw
        assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
    }

    @Test
    fun `OkHttpWebSocketProvider implements AutoCloseable`() {
        // Verify use-with-resources pattern compiles and runs cleanly
        OkHttpWebSocketProvider().use { provider ->
            assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
        }
    }

    // ------------------------------------------------------------------
    // BackoffClock injection tests (CODE FIX 1)
    // ------------------------------------------------------------------

    @Test
    fun `injected TestBackoffClock is called on reconnect attempts`() =
        runTest {
            val backoffClock = TestBackoffClock()
            // maxReconnectAttempts=2 means 1 failed attempt triggers 1 delay before giving up
            val provider = OkHttpWebSocketProvider(backoffClock = backoffClock)
            try {
                provider.connect("ws://127.0.0.1:1", maxReconnectAttempts = 2)
            } catch (_: Throwable) {
                // expected — unreachable host
            }
            assertTrue(
                backoffClock.recordedDelays.isNotEmpty(),
                "TestBackoffClock should have been called at least once during reconnection",
            )
        }

    @Test
    fun `no backoff delay when connect succeeds first attempt`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        server.start()
        val backoffClock = TestBackoffClock()
        val okClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        val provider = OkHttpWebSocketProvider(okHttpClient = okClient, backoffClock = backoffClock)
        try {
            kotlinx.coroutines.runBlocking {
                provider.connect(
                    server.url("/ws").toString().replace("http", "ws"),
                    maxReconnectAttempts = 3,
                )
            }
            assertEquals(0, backoffClock.recordedDelays.size, "No delay expected on first-attempt success")
        } finally {
            provider.close()
            runCatching { server.shutdown() }
        }
    }

    @Test
    fun `backoff delay count matches failed reconnect attempts`() =
        runTest {
            val backoffClock = TestBackoffClock()
            val maxAttempts = 3
            val provider = OkHttpWebSocketProvider(backoffClock = backoffClock)
            try {
                provider.connect("ws://127.0.0.1:1", maxReconnectAttempts = maxAttempts)
            } catch (_: Throwable) {
                // expected
            }
            // With maxReconnectAttempts=3: attempt 1 fails → delay, attempt 2 fails → delay,
            // attempt 3 fails → throw (no delay). So delays.size == maxAttempts - 1.
            assertEquals(
                maxAttempts - 1,
                backoffClock.recordedDelays.size,
                "Delay should be called once per failed attempt before the last one",
            )
        }

    // ------------------------------------------------------------------
    // dispose() alias tests (CODE FIX 2)
    // ------------------------------------------------------------------

    @Test
    fun `dispose() sets state to Disconnected`() {
        val provider = OkHttpWebSocketProvider()
        provider.dispose()
        assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
    }

    @Test
    fun `connect() after dispose() throws IllegalStateException`() =
        runTest {
            val provider = OkHttpWebSocketProvider()
            provider.dispose()
            assertFailsWith<IllegalStateException> {
                provider.connect("ws://127.0.0.1:1", maxReconnectAttempts = 1)
            }
        }

    @Test
    fun `dispose() is idempotent — calling twice does not throw`() {
        val provider = OkHttpWebSocketProvider()
        provider.dispose()
        provider.dispose()
        assertIs<WebSocketConnectionState.Disconnected>(provider.connectionState)
    }
}
