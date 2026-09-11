package com.syzygy.services.websocket

/**
 * Defines the contract for WebSocket connectivity.
 */
interface WebSocketProvider {
    /** Opens a WebSocket connection to the given [url]. */
    suspend fun connect(url: String)

    /** Sends a text [message] over the connection. */
    suspend fun send(message: String)

    /** Receives the next text message from the connection. */
    suspend fun receive(): String

    /** Closes the WebSocket connection. */
    fun disconnect()
}

/** Errors thrown by [StubWebSocketProvider]. */
sealed class WebSocketError(message: String) : Exception(message) {
    /** The WebSocket is not connected. */
    object NotConnected : WebSocketError("WebSocket is not connected")
}

/**
 * A stub [WebSocketProvider] for testing and as a placeholder for a real implementation.
 */
class StubWebSocketProvider : WebSocketProvider {
    private var connected = false

    override suspend fun connect(url: String) {
        connected = true
    }

    override suspend fun send(message: String) {
        if (!connected) throw WebSocketError.NotConnected
    }

    override suspend fun receive(): String {
        if (!connected) throw WebSocketError.NotConnected
        return ""
    }

    override fun disconnect() {
        connected = false
    }
}
