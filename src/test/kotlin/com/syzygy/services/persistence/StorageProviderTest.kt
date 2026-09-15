package com.syzygy.services.persistence

import com.syzygyhub.foundation.contracts.storage.StorageKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ------------------------------------------------------------------
    // ITEM 2 — Type-safe get() error tests
    // ------------------------------------------------------------------

    @Test
    fun `SharedPreferences type mismatch throws StorageTypeMismatchError with descriptive message`() {
        val store = SharedPreferencesStorageProvider()
        val key = StorageKey<Int>("number_key")
        // Store the raw string "not-a-number" directly via the string key to simulate a corrupt/mistyped entry
        val rawKey = StorageKey<String>("number_key")
        store.set("not-a-number", rawKey) { it }
        // Attempt to deserialize as Int — the lambda throws NumberFormatException
        val ex =
            assertFailsWith<StorageTypeMismatchError> {
                store.get(key) { it.toInt() }
            }
        assertTrue(ex.message.contains("number_key"), "Error message must include the key name")
        assertEquals("decoding_failed", ex.code.rawValue)
    }

    @Test
    fun `SharedPreferences correct type succeeds without throwing`() {
        val store = SharedPreferencesStorageProvider()
        val key = StorageKey<Int>("int_key")
        store.set(42, key) { it.toString() }
        val result = store.get(key) { it.toInt() }
        assertEquals(42, result)
    }

    @Test
    fun `EncryptedStorageProvider type mismatch throws StorageTypeMismatchError with descriptive message`() {
        val store = EncryptedStorageProvider()
        val rawKey = StorageKey<String>("enc_number_key")
        store.set("abc", rawKey) { it }
        val intKey = StorageKey<Int>("enc_number_key")
        val ex =
            assertFailsWith<StorageTypeMismatchError> {
                store.get(intKey) { it.toInt() }
            }
        assertTrue(ex.message.contains("enc_number_key"), "Error message must include the key name")
        assertEquals("decoding_failed", ex.code.rawValue)
    }

    @Test
    fun `EncryptedStorageProvider correct type succeeds without throwing`() {
        val store = EncryptedStorageProvider()
        val key = StorageKey<String>("enc_str_key")
        store.set("hello", key) { it }
        val result = store.get(key) { it }
        assertEquals("hello", result)
    }

    // ------------------------------------------------------------------
    // getTyped — reified type name in error messages
    // ------------------------------------------------------------------

    @Test
    fun `SharedPreferences getTyped error includes key name and concrete type names`() {
        val store = SharedPreferencesStorageProvider()
        // Store a string value that cannot be deserialized as Int
        val rawKey = StorageKey<String>("typed_key")
        store.set("not-an-int", rawKey) { it }
        val intKey = StorageKey<Int>("typed_key")
        val ex =
            assertFailsWith<StorageTypeMismatchError> {
                store.getTyped(intKey) { it.toInt() }
            }
        // Must contain the key name
        assertTrue(ex.message!!.contains("typed_key"), "Error must include key name, got: ${ex.message}")
        // Must contain the concrete requested type name — NOT "Unknown"
        assertTrue(ex.message!!.contains("Int"), "Error must include requested type 'Int', got: ${ex.message}")
        assertTrue(!ex.message!!.contains("Unknown"), "Error must not say 'Unknown', got: ${ex.message}")
        assertEquals("decoding_failed", ex.code.rawValue)
    }

    @Test
    fun `EncryptedStorageProvider getTyped error includes key name and concrete type names`() {
        val store = EncryptedStorageProvider()
        val rawKey = StorageKey<String>("enc_typed_key")
        store.set("not-an-int", rawKey) { it }
        val intKey = StorageKey<Int>("enc_typed_key")
        val ex =
            assertFailsWith<StorageTypeMismatchError> {
                store.getTyped(intKey) { it.toInt() }
            }
        assertTrue(ex.message!!.contains("enc_typed_key"), "Error must include key name, got: ${ex.message}")
        assertTrue(ex.message!!.contains("Int"), "Error must include requested type 'Int', got: ${ex.message}")
        assertTrue(!ex.message!!.contains("Unknown"), "Error must not say 'Unknown', got: ${ex.message}")
        assertEquals("decoding_failed", ex.code.rawValue)
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
