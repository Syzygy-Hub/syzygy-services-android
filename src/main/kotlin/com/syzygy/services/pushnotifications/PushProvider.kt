package com.syzygy.services.pushnotifications

/**
 * A parsed push notification payload.
 *
 * @property title The notification title, or `null` when absent.
 * @property body The notification body text, or `null` when absent.
 * @property data Arbitrary key-value data attached to the notification.
 * @property rawPayload The original raw payload string for debugging.
 */
data class NotificationPayload(
    val title: String? = null,
    val body: String? = null,
    val data: Map<String, String> = emptyMap(),
    val rawPayload: String = "",
)

/**
 * Contract for push-notification device registration and payload handling.
 *
 * Platform-level delivery (APNs / FCM) requires an Android [Context]; this
 * interface decouples that concern so that higher-level code can work with
 * a testable abstraction.
 */
interface PushProvider {
    /**
     * The current device push token, or `null` when the device has not yet
     * been registered.
     */
    val deviceToken: String?

    /**
     * Registers [token] as the current device push token.
     *
     * Call this after receiving a new token from the platform SDK (e.g. FCM's
     * `onNewToken` callback).
     */
    fun registerToken(token: String)

    /**
     * Clears the current device push token, effectively unregistering the
     * device from receiving push notifications.
     */
    fun unregisterToken()

    /**
     * Handles an incoming raw notification [payload] string.
     *
     * Parses the payload and returns a structured [NotificationPayload].
     * Implementations may additionally forward the payload to a delegate or
     * dispatch an in-app event.
     */
    fun handlePayload(payload: String): NotificationPayload
}

/**
 * Stub [PushProvider] suitable for tests and environments without a real FCM
 * connection.
 *
 * Payload parsing is simplified: keys and values are extracted from a
 * comma-separated `key=value` string (e.g. `"title=Hello,body=World"`). Real
 * implementations would parse the JSON delivered by FCM.
 */
class StubPushProvider : PushProvider {
    private var _deviceToken: String? = null

    /** The currently registered device push token. */
    override val deviceToken: String? get() = _deviceToken

    /** Stores [token] as the active push token. */
    override fun registerToken(token: String) {
        _deviceToken = token
    }

    /** Clears the active push token. */
    override fun unregisterToken() {
        _deviceToken = null
    }

    /**
     * Parses a simplified comma-separated `key=value` [payload] into a
     * [NotificationPayload].
     */
    override fun handlePayload(payload: String): NotificationPayload {
        val pairs =
            payload.split(",").mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx < 0) null else entry.substring(0, idx).trim() to entry.substring(idx + 1).trim()
            }.toMap()
        return NotificationPayload(
            title = pairs["title"],
            body = pairs["body"],
            data = pairs.filterKeys { it != "title" && it != "body" },
            rawPayload = payload,
        )
    }
}
