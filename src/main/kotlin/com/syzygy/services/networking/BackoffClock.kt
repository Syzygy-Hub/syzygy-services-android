package com.syzygy.services.networking

import kotlinx.coroutines.delay

/**
 * Abstraction for retry backoff timing, enabling deterministic testing.
 */
fun interface BackoffClock {
    suspend fun delay(attempt: Int)
}

/**
 * Canonical exponential backoff policy (full-jitter variant).
 *
 * Constants:
 *   BACKOFF_BASE_MS     = 500 ms  — initial window for attempt 0
 *   BACKOFF_MULTIPLIER  = 2.0     — doubles the window each attempt
 *   BACKOFF_CAP_MS      = 8000 ms — hard ceiling on the jitter window
 *   MAX_RETRY_ATTEMPTS  = 3       — callers should not exceed this
 *
 * Delay formula (full jitter):
 *   delay = random(0, min(BACKOFF_CAP_MS, BACKOFF_BASE_MS * 2^attempt))
 *
 * This avoids thundering-herd problems while ensuring delays stay bounded.
 */
class ExponentialBackoffClock(
    private val baseMs: Long = BACKOFF_BASE_MS,
) : BackoffClock {
    companion object {
        const val BACKOFF_BASE_MS: Long = 500L
        const val BACKOFF_MULTIPLIER: Double = 2.0
        const val BACKOFF_CAP_MS: Long = 8000L
        const val MAX_RETRY_ATTEMPTS: Int = 3
    }

    override suspend fun delay(attempt: Int) {
        val window = minOf(BACKOFF_CAP_MS, (baseMs * Math.pow(BACKOFF_MULTIPLIER, attempt.toDouble())).toLong())
        val jittered = (Math.random() * window).toLong()
        delay(jittered)
    }
}

// TestBackoffClock has been moved to src/test — see
// com.syzygy.services.networking.TestBackoffClock (test source set).
