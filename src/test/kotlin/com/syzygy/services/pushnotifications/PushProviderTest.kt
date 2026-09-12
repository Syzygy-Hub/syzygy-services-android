package com.syzygy.services.pushnotifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushProviderTest {
    @Test
    fun `initial device token is null`() {
        val provider = StubPushProvider()
        assertNull(provider.deviceToken)
    }

    @Test
    fun `registerToken stores token`() {
        val provider = StubPushProvider()
        provider.registerToken("fcm-token-abc")
        assertEquals("fcm-token-abc", provider.deviceToken)
    }

    @Test
    fun `unregisterToken clears token`() {
        val provider = StubPushProvider()
        provider.registerToken("tok")
        provider.unregisterToken()
        assertNull(provider.deviceToken)
    }

    @Test
    fun `handlePayload parses title and body`() {
        val provider = StubPushProvider()
        val payload = provider.handlePayload("title=Hello,body=World")
        assertEquals("Hello", payload.title)
        assertEquals("World", payload.body)
        assertEquals("title=Hello,body=World", payload.rawPayload)
    }

    @Test
    fun `handlePayload parses extra data fields`() {
        val provider = StubPushProvider()
        val payload = provider.handlePayload("title=Hi,body=Msg,order_id=42")
        assertEquals("42", payload.data["order_id"])
        assertTrue(payload.data.containsKey("order_id"))
    }

    @Test
    fun `handlePayload with empty string returns empty payload`() {
        val provider = StubPushProvider()
        val payload = provider.handlePayload("")
        assertNull(payload.title)
        assertNull(payload.body)
        assertTrue(payload.data.isEmpty())
    }
}
