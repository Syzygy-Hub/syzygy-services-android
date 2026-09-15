package com.syzygy.services.pushnotifications

/**
 * A parsed push notification payload.
 *
 * ## Building a payload
 * Use [NotificationPayload.build] for a concise, named-parameter construction:
 * ```kotlin
 * val payload = NotificationPayload.build(title = "Hello", body = "World") {
 *     data("action", "open_screen")
 *     data("screen", "home")
 * }
 * ```
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
) {
    /**
     * Mutable builder for [NotificationPayload].
     *
     * Obtain via [NotificationPayload.build].
     */
    class Builder(
        var title: String? = null,
        var body: String? = null,
        var rawPayload: String = "",
    ) {
        private val dataMap = mutableMapOf<String, String>()

        /**
         * Adds a key-value entry to [NotificationPayload.data].
         *
         * @param key The data key.
         * @param value The data value.
         */
        fun data(
            key: String,
            value: String,
        ): Builder {
            dataMap[key] = value
            return this
        }

        /**
         * Adds all entries from [map] to [NotificationPayload.data].
         *
         * @param map Entries to merge into the data map.
         */
        fun dataAll(map: Map<String, String>): Builder {
            dataMap.putAll(map)
            return this
        }

        /** Builds and returns the immutable [NotificationPayload]. */
        fun build(): NotificationPayload =
            NotificationPayload(
                title = title,
                body = body,
                data = dataMap.toMap(),
                rawPayload = rawPayload,
            )
    }

    companion object {
        /**
         * Factory / DSL builder for [NotificationPayload].
         *
         * ## Usage
         * ```kotlin
         * val p = NotificationPayload.build(title = "Alert", body = "Server is down") {
         *     data("severity", "critical")
         *     data("server", "prod-1")
         * }
         * ```
         *
         * @param title Optional notification title.
         * @param body Optional notification body text.
         * @param rawPayload The original raw payload string (for debugging).
         * @param block Optional DSL block for adding data entries and mutating other fields.
         */
        fun build(
            title: String? = null,
            body: String? = null,
            rawPayload: String = "",
            block: Builder.() -> Unit = {},
        ): NotificationPayload {
            val builder = Builder(title = title, body = body, rawPayload = rawPayload)
            builder.block()
            return builder.build()
        }

        /**
         * Parses a flat JSON notification payload string into a [NotificationPayload].
         *
         * The expected format is a JSON object with optional `"title"`, `"body"`,
         * and arbitrary data keys under a nested `"data"` object or at the top level.
         *
         * ```json
         * {"title":"Hello","body":"World","action":"open","screen":"home"}
         * ```
         *
         * ## Real FCM integration
         * In a production Android app, FCM delivers notifications via
         * `FirebaseMessagingService.onMessageReceived(RemoteMessage)`.  To integrate:
         *
         * 1. Add the `com.google.firebase:firebase-messaging` dependency to your app module.
         * 2. Declare a service extending `FirebaseMessagingService` in `AndroidManifest.xml`.
         * 3. In `onNewToken(token: String)`, call [PushProvider.registerToken] with the
         *    new FCM registration token so it can be sent to your back-end.
         * 4. In `onMessageReceived(message: RemoteMessage)`, construct a JSON string from
         *    `message.notification` and `message.data`, then call
         *    [PushProvider.handlePayload] to obtain a structured [NotificationPayload].
         * 5. Use the [NotificationPayload] to create an `android.app.Notification` via
         *    `NotificationCompat.Builder` and post it with `NotificationManagerCompat`.
         *
         * This module does **not** depend on `firebase-messaging` directly to remain a
         * pure JVM library testable without Android instrumentation.  The FCM dependency
         * belongs in the app module that consumes this library.
         *
         * @param json A flat JSON string, e.g. from FCM's `RemoteMessage.data` map
         *   serialised to JSON.
         */
        fun fromJson(json: String): NotificationPayload {
            val pattern = Regex(""""([^"]+)"\s*:\s*"([^"]*)"""")
            val pairs = pattern.findAll(json).associate { it.groupValues[1] to it.groupValues[2] }
            return NotificationPayload(
                title = pairs["title"],
                body = pairs["body"],
                data = pairs.filterKeys { it != "title" && it != "body" },
                rawPayload = json,
            )
        }
    }
}

/**
 * Contract for push-notification device registration and payload handling.
 *
 * Platform-level delivery via Firebase Cloud Messaging (FCM) requires an
 * Android `Context`; this interface decouples that concern so that higher-level
 * code can work with a testable abstraction.
 *
 * ## Real FCM integration steps
 *
 * ### 1. Add the dependency
 * In your **app** module's `build.gradle.kts` (not this library, which is
 * intentionally pure-JVM):
 * ```kotlin
 * implementation("com.google.firebase:firebase-messaging:23.x.x")
 * ```
 *
 * ### 2. Declare the service in AndroidManifest.xml
 * ```xml
 * <service
 *     android:name=".MyFirebaseMessagingService"
 *     android:exported="false">
 *   <intent-filter>
 *     <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *   </intent-filter>
 * </service>
 * ```
 *
 * ### 3. Implement FirebaseMessagingService
 * ```kotlin
 * class MyFirebaseMessagingService : FirebaseMessagingService() {
 *     override fun onNewToken(token: String) {
 *         pushProvider.registerToken(token)
 *         // Optionally upload the token to your backend here.
 *     }
 *
 *     override fun onMessageReceived(message: RemoteMessage) {
 *         val json = buildString {
 *             append("{")
 *             message.notification?.let {
 *                 append(""""title":"${it.title}","body":"${it.body}",""")
 *             }
 *             message.data.entries.joinTo(this) { (k, v) -> """"$k":"$v"""" }
 *             append("}")
 *         }
 *         val payload = pushProvider.handlePayload(json)
 *         // Show a notification using NotificationCompat.Builder.
 *     }
 * }
 * ```
 *
 * ### 4. Request the POST_NOTIFICATIONS permission (Android 13+)
 * ```kotlin
 * ActivityCompat.requestPermissions(
 *     activity,
 *     arrayOf(Manifest.permission.POST_NOTIFICATIONS),
 *     REQUEST_CODE,
 * )
 * ```
 *
 * ### 5. Retrieve the initial FCM token on first launch
 * ```kotlin
 * FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
 *     if (task.isSuccessful) pushProvider.registerToken(task.result)
 * }
 * ```
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
