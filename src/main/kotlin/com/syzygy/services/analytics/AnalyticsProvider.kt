package com.syzygy.services.analytics

/**
 * Defines the contract for recording analytics events.
 */
interface AnalyticsProvider {
    /** Records an event with the given [name] and optional [properties]. */
    fun track(
        name: String,
        properties: Map<String, String> = emptyMap(),
    )
}

/**
 * An [AnalyticsProvider] that logs events to the console.
 */
class ConsoleAnalyticsProvider : AnalyticsProvider {
    override fun track(
        name: String,
        properties: Map<String, String>,
    ) {
        println("[Analytics] $name $properties")
    }
}
