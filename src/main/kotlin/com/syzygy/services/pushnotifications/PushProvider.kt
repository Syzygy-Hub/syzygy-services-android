package com.syzygy.services.pushnotifications

/**
 * Defines the contract for push notification registration.
 */
interface PushProvider {
    /** Registers the device with the given FCM [token]. */
    fun registerToken(token: String)

    /** The current device push token, or null if not registered. */
    val deviceToken: String?
}

/**
 * A [PushProvider] that stores an FCM token in memory.
 */
class FCMPushProvider : PushProvider {
    private var storedToken: String? = null

    override fun registerToken(token: String) {
        storedToken = token
    }

    override val deviceToken: String? get() = storedToken
}
