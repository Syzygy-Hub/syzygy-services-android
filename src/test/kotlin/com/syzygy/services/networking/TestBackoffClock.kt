package com.syzygy.services.networking

/**
 * Test-only backoff that records requested delays without sleeping.
 * Inject this into [OkHttpNetworkClient] or [OkHttpWebSocketProvider] to verify
 * retry timing in tests.
 *
 * @param baseMs Unused — kept for API compatibility. The recorded delay values
 *   are computed by the production clock under test, not here.
 */
class TestBackoffClock(
    val recordedDelays: MutableList<Long> = mutableListOf(),
    @Suppress("UNUSED_PARAMETER") baseMs: Long = 0L,
) : BackoffClock {
    override suspend fun delay(attempt: Int) {
        recordedDelays.add((Math.pow(2.0, attempt.toDouble()) * 500L).toLong())
    }
}
