package com.syzygy.services.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageProviderTest {
    @Test
    fun `InMemoryStorageProvider round-trips a value`() {
        val provider = InMemoryStorageProvider()
        provider.set("key", "hello")
        assertEquals("hello", provider.get("key"))
        provider.remove("key")
        assertNull(provider.get("key"))
    }
}
