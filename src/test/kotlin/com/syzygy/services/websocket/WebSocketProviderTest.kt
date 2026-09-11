package com.syzygy.services.websocket

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class WebSocketProviderTest {
    @Test
    fun `StubWebSocketProvider initialises without error`() {
        val provider = StubWebSocketProvider()
        assertNotNull(provider)
    }

    @Test
    fun `StubWebSocketProvider connect and disconnect`() =
        runTest {
            val provider = StubWebSocketProvider()
            provider.connect("ws://localhost")
            provider.disconnect()
        }
}
