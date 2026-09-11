package com.syzygy.services.deviceservices

import kotlin.test.Test
import kotlin.test.assertNotNull

class DeviceProviderTest {
    @Test
    fun `BuildDeviceProvider returns non-null values`() {
        val provider = BuildDeviceProvider()
        assertNotNull(provider.manufacturer)
        assertNotNull(provider.model)
        assertNotNull(provider.osVersion)
    }
}
