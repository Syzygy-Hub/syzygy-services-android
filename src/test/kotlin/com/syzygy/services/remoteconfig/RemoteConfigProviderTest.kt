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

    // ------------------------------------------------------------------
    // Cache TTL tests (ITEM 3)
    // ------------------------------------------------------------------

    @Test
    fun `cacheTtlSeconds defaults to 3600`() {
        val provider = NetworkRemoteConfigProvider()
        assertEquals(3600L, provider.cacheTtlSeconds)
    }

    @Test
    fun `fetch skips network call when cache is within TTL`() =
        runTest {
            val server = okhttp3.mockwebserver.MockWebServer()
            server.start()
            // First response seeds the cache
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"color":"blue"}"""),
            )
            var fakeNow = System.currentTimeMillis()
            val client = com.syzygy.services.networking.OkHttpNetworkClient(maxRetries = 1)
            val provider =
                NetworkRemoteConfigProvider(
                    networkClient = client,
                    configUrl = server.url("/config").toString(),
                    cacheTtlSeconds = 60L,
                    clock = { fakeNow },
                )

            // First fetch — hits network
            provider.fetch()
            assertEquals(1, server.requestCount)
            assertEquals("blue", provider.getString("color"))

            // Advance time by 30 seconds (within TTL of 60s)
            fakeNow += 30_000

            // Second fetch — should use cache, not hit network
            provider.fetch()
            assertEquals(1, server.requestCount, "Should still be 1 request — cache is fresh")

            server.shutdown()
        }

    @Test
    fun `fetch hits network when cache is stale (beyond TTL)`() =
        runTest {
            val server = okhttp3.mockwebserver.MockWebServer()
            server.start()
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"color":"red"}"""),
            )
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"color":"green"}"""),
            )
            var fakeNow = System.currentTimeMillis()
            val client = com.syzygy.services.networking.OkHttpNetworkClient(maxRetries = 1)
            val provider =
                NetworkRemoteConfigProvider(
                    networkClient = client,
                    configUrl = server.url("/config").toString(),
                    cacheTtlSeconds = 60L,
                    clock = { fakeNow },
                )

            // First fetch
            provider.fetch()
            assertEquals("red", provider.getString("color"))
            assertEquals(1, server.requestCount)

            // Advance 90 seconds — beyond the 60s TTL
            fakeNow += 90_000

            // Second fetch — cache is stale, must hit network
            provider.fetch()
            assertEquals(2, server.requestCount, "Should have made a second network request")
            assertEquals("green", provider.getString("color"))

            server.shutdown()
        }

    @Test
    fun `fetch always hits network on first call when lastFetchTime is null`() =
        runTest {
            val server = okhttp3.mockwebserver.MockWebServer()
            server.start()
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"k":"v"}"""),
            )
            val client = com.syzygy.services.networking.OkHttpNetworkClient(maxRetries = 1)
            val provider =
                NetworkRemoteConfigProvider(
                    networkClient = client,
                    configUrl = server.url("/config").toString(),
                    cacheTtlSeconds = 3600L,
                )
            assertNull(provider.lastFetchTime)
            provider.fetch()
            assertNotNull(provider.lastFetchTime)
            assertEquals(1, server.requestCount)
            server.shutdown()
        }
}
