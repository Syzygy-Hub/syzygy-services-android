package com.syzygy.services.crashreporting

import java.util.concurrent.ConcurrentHashMap

/**
 * Contract for crash and non-fatal error reporting.
 */
interface CrashReporter {
    /**
     * Records a non-fatal [error] with optional [metadata].
     *
     * Implementations should transmit the error to the configured crash
     * reporting backend without interrupting the running process.
     *
     * @param error The [Throwable] to record.
     * @param metadata Additional key-value context attached to the report.
     */
    fun recordError(
        error: Throwable,
        metadata: Map<String, String> = emptyMap(),
    )

    /**
     * Reports a fatal crash described by [message] with optional [metadata].
     *
     * Implementations **must not** actually terminate the process — the name
     * "fatal" refers to the severity classification sent to the backend, not
     * the process lifecycle.
     *
     * @param message A human-readable description of the crash.
     * @param metadata Additional key-value context attached to the report.
     */
    fun reportCrash(
        message: String,
        metadata: Map<String, String> = emptyMap(),
    )

    /**
     * Associates subsequent reports with the given [userId] and [email].
     *
     * @param userId A stable user identifier.
     * @param email The user's email address (optional).
     */
    fun setUserContext(
        userId: String,
        email: String? = null,
    )

    /**
     * Attaches a custom [value] to all subsequent reports under [key].
     *
     * Custom keys provide additional context beyond what [setUserContext]
     * captures (e.g. `"build_flavor"`, `"experiment_group"`).
     */
    fun setCustomKey(
        key: String,
        value: String,
    )

    /**
     * Leaves a breadcrumb with [message] and optional [metadata].
     *
     * Breadcrumbs are lightweight trail markers that implementations include
     * in crash reports to show the sequence of events leading up to the crash.
     * Implementations may cap the number of retained breadcrumbs.
     *
     * @param message A short human-readable description of the event.
     * @param metadata Optional key-value context for this breadcrumb.
     */
    fun leaveBreadcrumb(
        message: String,
        metadata: Map<String, String>? = null,
    )

    /**
     * Removes all stored breadcrumbs.
     */
    fun clearBreadcrumbs()
}

/**
 * A single breadcrumb entry retained by [ConsoleCrashReporter].
 *
 * @property message A short description of the event.
 * @property metadata Optional key-value context.
 */
data class Breadcrumb(
    val message: String,
    val metadata: Map<String, String>?,
)

/**
 * [CrashReporter] that writes reports to standard output.
 *
 * Suitable for development builds and automated tests. Production targets
 * should replace this with an integration to a real crash-reporting SDK
 * (e.g. Firebase Crashlytics, Sentry).
 *
 * All user context and custom keys are stored in memory and included in
 * each report line.
 */
class ConsoleCrashReporter : CrashReporter {
    /** Current user identifier, set via [setUserContext]. */
    private var userId: String? = null

    /** Current user email, set via [setUserContext]. */
    private var userEmail: String? = null

    /** Custom key-value metadata attached to all subsequent reports. */
    private val customKeys = ConcurrentHashMap<String, String>()

    /** Circular buffer holding the last [MAX_BREADCRUMBS] breadcrumbs. */
    private val breadcrumbs = ArrayDeque<Breadcrumb>(MAX_BREADCRUMBS)

    companion object {
        /** Maximum number of breadcrumbs retained at any time. */
        const val MAX_BREADCRUMBS = 20
    }

    /**
     * Prints [error] and its [metadata] to standard output, including stored breadcrumbs.
     */
    override fun recordError(
        error: Throwable,
        metadata: Map<String, String>,
    ) {
        val cls = error::class.simpleName
        val msg = error.message
        val crumbs = getBreadcrumbs()
        println(
            "[CrashReporter] NON_FATAL error=$cls message=$msg metadata=$metadata " +
                "user=$userId keys=$customKeys breadcrumbs=$crumbs",
        )
    }

    /**
     * Prints [message] as a fatal crash report to standard output, including stored breadcrumbs.
     *
     * No process termination occurs.
     */
    override fun reportCrash(
        message: String,
        metadata: Map<String, String>,
    ) {
        val crumbs = getBreadcrumbs()
        println(
            "[CrashReporter] FATAL message=$message metadata=$metadata " +
                "user=$userId keys=$customKeys breadcrumbs=$crumbs",
        )
    }

    /**
     * Stores [userId] and [email] and includes them in all subsequent reports.
     */
    override fun setUserContext(
        userId: String,
        email: String?,
    ) {
        this.userId = userId
        this.userEmail = email
    }

    /** Stores [key]/[value] and includes it in all subsequent reports. */
    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        customKeys[key] = value
    }

    /** Returns a snapshot of the currently stored custom keys (for inspection in tests). */
    fun getCustomKeys(): Map<String, String> = customKeys.toMap()

    /** Returns the currently configured user ID. */
    fun getUserId(): String? = userId

    /** Returns the currently configured user email. */
    fun getUserEmail(): String? = userEmail

    /**
     * Adds [message] and optional [metadata] to the breadcrumb circular buffer.
     *
     * When the buffer is full ([MAX_BREADCRUMBS] entries) the oldest breadcrumb
     * is evicted before the new one is added.
     */
    override fun leaveBreadcrumb(
        message: String,
        metadata: Map<String, String>?,
    ) {
        synchronized(breadcrumbs) {
            if (breadcrumbs.size >= MAX_BREADCRUMBS) {
                breadcrumbs.removeFirst()
            }
            breadcrumbs.addLast(Breadcrumb(message, metadata))
        }
        println("[CrashReporter] BREADCRUMB message=$message metadata=$metadata")
    }

    /** Removes all stored breadcrumbs. */
    override fun clearBreadcrumbs() {
        synchronized(breadcrumbs) {
            breadcrumbs.clear()
        }
    }

    /** Returns a snapshot of all stored breadcrumbs in chronological order (for inspection in tests). */
    fun getBreadcrumbs(): List<Breadcrumb> =
        synchronized(breadcrumbs) {
            breadcrumbs.toList()
        }
}
