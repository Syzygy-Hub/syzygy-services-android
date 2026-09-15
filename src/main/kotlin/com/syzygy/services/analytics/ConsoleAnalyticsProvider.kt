package com.syzygy.services.analytics

import com.syzygyhub.foundation.contracts.analytics.AnalyticsEvent
import com.syzygyhub.foundation.contracts.analytics.AnalyticsProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [AnalyticsProvider] that prints events to standard output.
 *
 * This implementation is suitable for development builds and automated tests.
 * Production targets should replace it with an integration to a real
 * analytics backend (e.g. Amplitude, Mixpanel, Firebase Analytics).
 *
 * In addition to the Foundation [AnalyticsProvider] contract, this class
 * provides [trackScreen] for screen-view events and basic session management
 * via [sessionId].
 */
class ConsoleAnalyticsProvider : AnalyticsProvider {
    /**
     * A random UUID that identifies the current analytics session.
     *
     * A new session begins each time a [ConsoleAnalyticsProvider] is
     * instantiated or [reset] is called. Production implementations would
     * persist the session ID across restarts or regenerate it after a period
     * of inactivity.
     */
    var sessionId: String = UUID.randomUUID().toString()
        private set

    /** In-memory user properties set via [identify]. */
    private val userProperties = ConcurrentHashMap<String, String>()

    /** The user ID associated with the current session, or `null`. */
    private var currentUserId: String? = null

    /**
     * Prints [event] to standard output in a structured format, with [sessionId]
     * injected into the event's metadata under `"session_id"`.
     *
     * @param event The [AnalyticsEvent] to record.
     */
    override fun track(event: AnalyticsEvent) {
        val enrichedProps = event.properties + mapOf("session_id" to sessionId)
        println(
            "[Analytics] event=${event.name} props=$enrichedProps " +
                "ts=${event.timestamp.millisecondsSinceEpoch} session=$sessionId",
        )
    }

    /**
     * Associates the current session with [userId] and stores [traits] as
     * user properties.
     *
     * @param userId The identifier for the current user.
     * @param traits Key-value pairs describing the user.
     */
    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        currentUserId = userId
        userProperties.clear()
        userProperties.putAll(traits)
        println("[Analytics] identify userId=$userId traits=$traits session=$sessionId")
    }

    /**
     * Clears the current user identity and all stored user properties, and
     * generates a fresh [sessionId] to begin a new analytics session.
     */
    override fun reset() {
        currentUserId = null
        userProperties.clear()
        val oldSession = sessionId
        sessionId = UUID.randomUUID().toString()
        println("[Analytics] reset oldSession=$oldSession newSession=$sessionId")
    }

    /**
     * Tracks a screen-view event named `"screen.viewed"` with the given
     * [screenName] as a property.
     *
     * @param screenName The name of the screen that was displayed.
     * @param properties Additional properties to attach to the screen event.
     */
    fun trackScreen(
        screenName: String,
        properties: Map<String, String> = emptyMap(),
    ) {
        val merged = properties + mapOf("screen_name" to screenName)
        track(AnalyticsEvent(name = "screen.viewed", properties = merged))
    }

    /**
     * Returns a snapshot of the currently stored user properties.
     *
     * Useful for inspection in tests.
     */
    fun getUserProperties(): Map<String, String> = userProperties.toMap()

    /** Returns the current user ID, or `null` if [identify] has not been called. */
    fun getCurrentUserId(): String? = currentUserId
}
