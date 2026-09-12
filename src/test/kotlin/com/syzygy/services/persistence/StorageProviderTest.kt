package com.syzygy.services.persistence

import com.syzygyhub.foundation.contracts.storage.StorageKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageProviderTest {
    // ------------------------------------------------------------------
    // SharedPreferencesStorageProvider
    // ------------------------------------------------------------------

    @Test
    fun `SharedPreferences set and get roundtrip string`() {
        val store = SharedPreferencesStorageProvider()
        val key = StorageKey<String>("name")
        store.set("Alice", key) { it }
        assertEquals("Alice", store.get(key) { it })
    }

    @Test
    fun `SharedPreferences missing key returns null`() {
        val store = SharedPreferencesStorageProvider()
        val key = StorageKey<String>("missing")
        assertNull(store.get(key) { it })
    }

    @Test
    fun `SharedPreferences missing key returns defaultValue when set`() {
        val store = SharedPreferencesStorageProvider()
        val key = StorageKey("count", defaultValue = 42)
        assertEquals(42, store.get(key) { it.toInt() })
    }

    @Test
    fun `SharedPreferences remove clears value`() {
        val store = SharedPreferencesStorageProvider()
        val key = StorageKey<String>("item")
        store.set("value", key) { it }
        store.remove(key)
        assertNull(store.get(key) { it })
    }

    @Test
    fun `SharedPreferences clear removes all keys`() {
        val store = SharedPreferencesStorageProvider()
        val k1 = StorageKey<String>("a")
        val k2 = StorageKey<String>("b")
        store.set("x", k1) { it }
        store.set("y", k2) { it }
        store.clear()
        assertNull(store.get(k1) { it })
        assertNull(store.get(k2) { it })
    }

    // ------------------------------------------------------------------
    // EncryptedStorageProvider
    // ------------------------------------------------------------------

    @Test
    fun `EncryptedStorageProvider set and get roundtrip string`() {
        val store = EncryptedStorageProvider()
        val key = StorageKey<String>("secret")
        store.set("p@ssw0rd", key) { it }
        assertEquals("p@ssw0rd", store.get(key) { it })
    }

    @Test
    fun `EncryptedStorageProvider missing key returns null`() {
        val store = EncryptedStorageProvider()
        val key = StorageKey<String>("absent")
        assertNull(store.get(key) { it })
    }

    @Test
    fun `EncryptedStorageProvider remove clears value`() {
        val store = EncryptedStorageProvider()
        val key = StorageKey<String>("tok")
        store.set("abc", key) { it }
        store.remove(key)
        assertNull(store.get(key) { it })
    }

    @Test
    fun `EncryptedStorageProvider clear removes all entries`() {
        val store = EncryptedStorageProvider()
        val k1 = StorageKey<String>("x")
        val k2 = StorageKey<String>("y")
        store.set("1", k1) { it }
        store.set("2", k2) { it }
        store.clear()
        assertNull(store.get(k1) { it })
        assertNull(store.get(k2) { it })
    }

    @Test
    fun `EncryptedStorageProvider stores different instances independently`() {
        val key = EncryptedStorageProvider.generateAesKey()
        val store1 = EncryptedStorageProvider(key)
        val store2 = EncryptedStorageProvider(key)
        val storageKey = StorageKey<String>("shared")
        store1.set("hello", storageKey) { it }
        // store2 shares the key but is a different in-memory map — should not see store1 values
        assertNull(store2.get(storageKey) { it })
    }
}
