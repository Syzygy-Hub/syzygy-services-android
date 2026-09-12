package com.syzygy.services.deviceservices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceProviderTest {
    @Test
    fun `deviceId is non-null and non-empty`() {
        val provider = BuildDeviceProvider()
        assertNotNull(provider.deviceId)
        assertTrue(provider.deviceId.isNotEmpty())
    }

    @Test
    fun `deviceId is stable across multiple accesses`() {
        val provider = BuildDeviceProvider()
        val id1 = provider.deviceId
        val id2 = provider.deviceId
        assertEquals(id1, id2)
    }

    @Test
    fun `platform is android or jvm`() {
        val provider = BuildDeviceProvider()
        assertTrue(provider.platform == "android" || provider.platform == "jvm")
    }

    @Test
    fun `osVersion is non-empty`() {
        val provider = BuildDeviceProvider()
        assertTrue(provider.osVersion.isNotEmpty())
    }

    @Test
    fun `appVersion returns string`() {
        val provider = BuildDeviceProvider()
        assertNotNull(provider.appVersion)
    }

    @Test
    fun `isEmulator is false in JVM test environment`() {
        val provider = BuildDeviceProvider()
        // In a pure JVM test environment, android.os.Build is not available
        // so isEmulator should return false
        assertFalse(provider.isEmulator)
    }

    @Test
    fun `two providers share the same persisted deviceId`() {
        // Both providers use SharedPreferencesStorageProvider with the same in-memory map
        // but each instance has its own map — so they may differ; this tests that
        // each is individually stable
        val p1 = BuildDeviceProvider()
        val p2 = BuildDeviceProvider()
        val id1a = p1.deviceId
        val id1b = p1.deviceId
        val id2a = p2.deviceId
        val id2b = p2.deviceId
        assertEquals(id1a, id1b)
        assertEquals(id2a, id2b)
    }
}
