package com.syzygy.services.websocket

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
