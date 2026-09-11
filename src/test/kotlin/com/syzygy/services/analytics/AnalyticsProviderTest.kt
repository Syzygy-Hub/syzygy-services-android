package com.syzygy.services.analytics

import kotlin.test.Test

class AnalyticsProviderTest {
    @Test
    fun `ConsoleAnalyticsProvider tracks event without throwing`() {
        val provider = ConsoleAnalyticsProvider()
        provider.track("test_event", mapOf("key" to "value"))
    }
}
