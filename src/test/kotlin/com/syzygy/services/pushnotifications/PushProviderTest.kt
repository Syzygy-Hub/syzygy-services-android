package com.syzygy.services.pushnotifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushProviderTest {
    @Test
    fun `FCMPushProvider stores token`() {
        val provider = FCMPushProvider()
        assertNull(provider.deviceToken)
        provider.registerToken("my-fcm-token")
        assertEquals("my-fcm-token", provider.deviceToken)
    }
}
