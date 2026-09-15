package com.syzygy.services.networking

// Renamed to BackoffClock. See BackoffClock.kt.
@Deprecated("Use BackoffClock", ReplaceWith("BackoffClock"))
typealias BackoffStrategy = BackoffClock

@Deprecated("Use ExponentialBackoffClock", ReplaceWith("ExponentialBackoffClock"))
typealias ExponentialBackoffStrategy = ExponentialBackoffClock

@Deprecated("Use TestBackoffClock", ReplaceWith("TestBackoffClock"))
typealias TestBackoffStrategy = TestBackoffClock
