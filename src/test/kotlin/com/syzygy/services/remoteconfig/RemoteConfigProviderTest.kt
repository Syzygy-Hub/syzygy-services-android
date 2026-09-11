package com.syzygy.services.remoteconfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteConfigProviderTest {
    @Test
    fun `InMemoryRemoteConfigProvider round-trips string and bool`() {
        val provider = InMemoryRemoteConfigProvider()
        provider.setValue("greeting", "world")
        provider.setValue("flag", true)
        assertEquals("world", provider.getString("greeting"))
        assertTrue(provider.getBoolean("flag") == true)
        assertNull(provider.getString("missing"))
    }
}
