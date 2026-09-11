package com.syzygy.services.crashreporting

/**
 * Defines the contract for crash and non-fatal error reporting.
 */
interface CrashReporter {
    /** Reports a fatal crash with the given [message] and optional [metadata]. */
    fun reportCrash(
        message: String,
        metadata: Map<String, String> = emptyMap(),
    )

    /** Records a non-fatal [error] with optional [metadata]. */
    fun recordError(
        error: Throwable,
        metadata: Map<String, String> = emptyMap(),
    )
}

/**
 * A [CrashReporter] that logs crash and error events to the console.
 */
class ConsoleCrashReporter : CrashReporter {
    override fun reportCrash(
        message: String,
        metadata: Map<String, String>,
    ) {
        println("[CrashReporter] CRASH: $message $metadata")
    }

    override fun recordError(
        error: Throwable,
        metadata: Map<String, String>,
    ) {
        println("[CrashReporter] ERROR: ${error.message} $metadata")
    }
}
