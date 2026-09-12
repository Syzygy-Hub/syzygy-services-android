package com.syzygy.services.websocket

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The current connection state of a [WebSocketProvider].
 */
sealed class WebSocketConnectionState {
    /** Not connected. May carry the last error that caused a disconnect. */
    data class Disconnected(val cause: Throwable? = null) : WebSocketConnectionState()

    /** A connection attempt is in progress. */
    object Connecting : WebSocketConnectionState()

    /** The WebSocket handshake completed and the connection is open. */
    object Connected : WebSocketConnectionState()
}

/**
 * Contract for WebSocket connectivity.
 */
interface WebSocketProvider {
    /** The current connection state. */
    val connectionState: WebSocketConnectionState

    /**
     * A [Flow] of text messages received from the server.
     *
     * The flow remains active until [disconnect] is called.
     */
    val messages: Flow<String>

    /**
     * Opens a WebSocket connection to [url].
     *
     * Suspends until the connection is established. Reconnects automatically
     * on transient failures with exponential back-off up to [maxReconnectAttempts]
     * times.
     *
     * @param url The WebSocket URL (e.g. `"ws://example.com/socket"`).
     * @param maxReconnectAttempts Maximum automatic reconnection attempts.
     */
    suspend fun connect(
        url: String,
        maxReconnectAttempts: Int = 3,
    )

    /**
     * Sends a text [message] over the open connection.
     *
     * @throws IllegalStateException when the connection is not [WebSocketConnectionState.Connected].
     */
    suspend fun sendText(message: String)

    /**
     * Sends a binary [data] frame over the open connection.
     *
     * @throws IllegalStateException when the connection is not [WebSocketConnectionState.Connected].
     */
    suspend fun sendBinary(data: ByteArray)

    /**
     * Closes the WebSocket connection gracefully.
     *
     * [messages] will complete after this call returns.
     */
    fun disconnect()
}

/**
 * OkHttp-backed [WebSocketProvider] with automatic reconnection.
 *
 * @param okHttpClient The [OkHttpClient] used to establish the connection.
 *   If not provided, a default client with 10-second timeouts is used.
 */
@Suppress("PrivatePropertyName")
class OkHttpWebSocketProvider(
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for long-lived WS connections
            .build(),
) : WebSocketProvider {
    private val stateRef = AtomicReference<WebSocketConnectionState>(WebSocketConnectionState.Disconnected())
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    private var activeSocket: WebSocket? = null
    private var lastUrl: String = ""

    /** The current connection state. */
    override val connectionState: WebSocketConnectionState get() = stateRef.get()

    /** Flow of text messages received from the server. */
    override val messages: Flow<String> = messageChannel.receiveAsFlow()

    /**
     * Connects to [url] with automatic reconnection on failure.
     */
    override suspend fun connect(
        url: String,
        maxReconnectAttempts: Int,
    ) {
        lastUrl = url
        val attempt = AtomicInteger(0)
        while (true) {
            stateRef.set(WebSocketConnectionState.Connecting)
            val result = openSocket(url)
            if (result == null) {
                // Connected
                return
            }
            if (attempt.incrementAndGet() >= maxReconnectAttempts) {
                stateRef.set(WebSocketConnectionState.Disconnected(result))
                throw result
            }
            val backoffMs = (2.0.pow(attempt.get()) * 500).toLong()
            delay(backoffMs)
        }
    }

    /**
     * Attempts to open a WebSocket to [url].
     *
     * @return `null` on success, or the [Throwable] that caused the failure.
     */
    private suspend fun openSocket(url: String): Throwable? {
        val connected = Channel<Throwable?>(1)
        val request = Request.Builder().url(url).build()
        val listener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    activeSocket = webSocket
                    stateRef.set(WebSocketConnectionState.Connected)
                    connected.trySend(null)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    messageChannel.trySend(text)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    bytes: ByteString,
                ) {
                    messageChannel.trySend(bytes.utf8())
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    stateRef.set(WebSocketConnectionState.Disconnected(t))
                    connected.trySend(t)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    stateRef.set(WebSocketConnectionState.Disconnected())
                }
            }
        okHttpClient.newWebSocket(request, listener)
        return connected.receive()
    }

    /**
     * Sends a text [message] over the active connection.
     *
     * @throws IllegalStateException when not connected.
     */
    override suspend fun sendText(message: String) {
        val socket = activeSocket
        check(socket != null && stateRef.get() is WebSocketConnectionState.Connected) {
            "Cannot send: WebSocket is not connected"
        }
        socket.send(message)
    }

    /**
     * Sends [data] as a binary frame over the active connection.
     *
     * @throws IllegalStateException when not connected.
     */
    override suspend fun sendBinary(data: ByteArray) {
        val socket = activeSocket
        check(socket != null && stateRef.get() is WebSocketConnectionState.Connected) {
            "Cannot send binary: WebSocket is not connected"
        }
        socket.send(data.toByteString())
    }

    /** Closes the active WebSocket connection gracefully. */
    override fun disconnect() {
        activeSocket?.close(1000, "Client disconnect")
        activeSocket = null
        stateRef.set(WebSocketConnectionState.Disconnected())
        messageChannel.close()
    }
}

/** Integer power helper. */
private fun Double.pow(exp: Int): Double {
    var result = 1.0
    repeat(exp) { result *= this }
    return result
}
