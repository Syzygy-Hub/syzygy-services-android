package com.syzygy.services.networking

import kotlinx.coroutines.delay

/**
 * Abstraction for retry backoff timing, enabling deterministic testing.
 */
fun interface BackoffClock {
    suspend fun delay(attempt: Int)
}

/**
 * Default exponential backoff: 2^attempt * baseMs milliseconds.
 */
class ExponentialBackoffClock(
    private val baseMs: Long = 500L,
) : BackoffClock {
    override suspend fun delay(attempt: Int) {
        delay((Math.pow(2.0, attempt.toDouble()) * baseMs).toLong())
    }
}

/**
 * Test-only backoff that records requested delays without sleeping.
 * Inject this into [OkHttpNetworkClient] to verify retry timing in tests.
 */
class TestBackoffClock(
    val recordedDelays: MutableList<Long> = mutableListOf(),
) : BackoffClock {
    override suspend fun delay(attempt: Int) {
        recordedDelays.add((Math.pow(2.0, attempt.toDouble()) * 500L).toLong())
    }
}
