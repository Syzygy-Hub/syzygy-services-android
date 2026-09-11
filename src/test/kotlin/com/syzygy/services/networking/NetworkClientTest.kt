package com.syzygy.services.networking

import kotlin.test.Test
import kotlin.test.assertNotNull

class NetworkClientTest {
    @Test
    fun `CoroutineNetworkClient initialises without error`() {
        val client = CoroutineNetworkClient()
        assertNotNull(client)
    }
}
