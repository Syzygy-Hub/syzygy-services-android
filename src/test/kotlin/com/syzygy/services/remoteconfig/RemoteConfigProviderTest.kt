package com.syzygy.services.remoteconfig

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteConfigProviderTest {
    @Test
    fun `default values returned when key absent`() {
        val provider = NetworkRemoteConfigProvider(defaults = emptyMap())
        assertEquals("", provider.getString("missing"))
        assertEquals(0, provider.getInt("missing"))
        assertEquals(false, provider.getBoolean("missing"))
        assertEquals(0.0, provider.getDouble("missing"))
    }

    @Test
    fun `setValue then getString returns value`() {
        val provider = NetworkRemoteConfigProvider()
        provider.setValue("greeting", "hello")
        assertEquals("hello", provider.getString("greeting"))
    }

    @Test
    fun `getInt parses integer string`() {
        val provider = NetworkRemoteConfigProvider()
        provider.setValue("timeout", "30")
        assertEquals(30, provider.getInt("timeout"))
    }

    @Test
    fun `getBoolean parses boolean string`() {
        val provider = NetworkRemoteConfigProvider()
        provider.setValue("flag", "true")
        assertTrue(provider.getBoolean("flag"))
    }

    @Test
    fun `getDouble parses double string`() {
        val provider = NetworkRemoteConfigProvider()
        provider.setValue("rate", "1.5")
        assertEquals(1.5, provider.getDouble("rate"))
    }

    @Test
    fun `defaults map is used as initial values`() {
        val provider = NetworkRemoteConfigProvider(defaults = mapOf("env" to "staging"))
        assertEquals("staging", provider.getString("env"))
    }

    @Test
    fun `lastFetchTime is null before any fetch`() {
        val provider = NetworkRemoteConfigProvider()
        assertNull(provider.lastFetchTime)
    }

    @Test
    fun `fetch with no networkClient is a no-op`() =
        runTest {
            val provider = NetworkRemoteConfigProvider()
            provider.fetch() // should not throw
            assertNull(provider.lastFetchTime)
        }

    @Test
    fun `fetch with real server updates lastFetchTime`() =
        runTest {
            val server = okhttp3.mockwebserver.MockWebServer()
            server.start()
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"theme":"dark","max_retries":"5"}"""),
            )
            val client = com.syzygy.services.networking.OkHttpNetworkClient(maxRetries = 1)
            val provider =
                NetworkRemoteConfigProvider(
                    networkClient = client,
                    configUrl = server.url("/config").toString(),
                )
            provider.fetch()
            assertNotNull(provider.lastFetchTime)
            assertEquals("dark", provider.getString("theme"))
            assertEquals(5, provider.getInt("max_retries"))
            server.shutdown()
        }
}
