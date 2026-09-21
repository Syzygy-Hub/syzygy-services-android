package com.syzygy.services.networking

// Renamed to BackoffClock. See BackoffClock.kt.
@Deprecated("Use BackoffClock", ReplaceWith("BackoffClock"))
typealias BackoffStrategy = BackoffClock

@Deprecated("Use ExponentialBackoffClock", ReplaceWith("ExponentialBackoffClock"))
typealias ExponentialBackoffStrategy = ExponentialBackoffClock

// TestBackoffStrategy alias removed — TestBackoffClock is now test-only (src/test).
// Use com.syzygy.services.networking.TestBackoffClock in test sources directly.
