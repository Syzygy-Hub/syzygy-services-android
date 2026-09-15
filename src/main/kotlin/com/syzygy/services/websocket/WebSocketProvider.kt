package com.syzygy.services.websocket

import com.syzygy.services.networking.BackoffClock
import com.syzygy.services.networking.ExponentialBackoffClock
import kotlinx.coroutines.channels.Channel
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
     * A [Flow] of binary frames received from the server as raw [ByteArray]s.
     *
     * The flow remains active until [disconnect] is called.  Binary frames
     * are not converted to strings — they are emitted here in their original
     * byte form.
     */
    val binaryMessages: Flow<ByteArray>

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

    /**
     * Alias for [close] — releases all resources held by this provider.
     *
     * Prefer [close] when using the [AutoCloseable] / try-with-resources pattern.
     * [dispose] is provided for callers following the cross-platform Syzygy
     * dispose convention.
     */
    fun dispose()
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
    private val backoffClock: BackoffClock = ExponentialBackoffClock(),
) : WebSocketProvider, AutoCloseable {
    private val stateRef = AtomicReference<WebSocketConnectionState>(WebSocketConnectionState.Disconnected())
    private val messageChannel = Channel<String>(Channel.UNLIMITED)
    private val binaryChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var activeSocket: WebSocket? = null
    private var lastUrl: String = ""

    /** Whether this provider has been closed via [close]. */
    @Volatile
    private var closed = false

    /** The current connection state. */
    override val connectionState: WebSocketConnectionState get() = stateRef.get()

    /** Flow of text messages received from the server. */
    override val messages: Flow<String> = messageChannel.receiveAsFlow()

    /**
     * Flow of binary frames received from the server.
     *
     * Frames arrive here as raw [ByteArray]s without any UTF-8 conversion,
     * making it suitable for binary protocols (e.g. protobuf, MessagePack).
     */
    override val binaryMessages: Flow<ByteArray> = binaryChannel.receiveAsFlow()

    /**
     * Connects to [url] with automatic reconnection on failure.
     *
     * @throws IllegalStateException when this provider has been [close]d.
     */
    override suspend fun connect(
        url: String,
        maxReconnectAttempts: Int,
    ) {
        check(!closed) { "OkHttpWebSocketProvider has been closed" }
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
            backoffClock.delay(attempt.get())
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
                    // Emit on binaryMessages as raw bytes; also forward UTF-8
                    // decoded text to the messages channel for backward compatibility.
                    binaryChannel.trySend(bytes.toByteArray())
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
        binaryChannel.close()
    }

    /**
     * Releases all resources held by this provider.
     *
     * Closes the active WebSocket connection, cancels the OkHttp dispatcher,
     * and evicts all pooled connections. After [close] is called, any attempt
     * to [connect], [sendText], or [sendBinary] will throw [IllegalStateException].
     * [disconnect] remains safe to call on a closed provider (it becomes a no-op).
     */
    override fun close() {
        closed = true
        disconnect()
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
    }

    /**
     * Alias for [close] — releases all resources held by this provider.
     *
     * @see close
     */
    override fun dispose() = close()
}
