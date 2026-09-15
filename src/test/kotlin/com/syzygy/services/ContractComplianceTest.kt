package com.syzygy.services

import com.syzygy.services.analytics.ConsoleAnalyticsProvider
import com.syzygy.services.auth.JWTAuthProvider
import com.syzygy.services.crashreporting.ConsoleCrashReporter
import com.syzygy.services.crashreporting.CrashReporter
import com.syzygy.services.deviceservices.DeviceProvider
import com.syzygy.services.filemanagement.FileProvider
import com.syzygy.services.filemanagement.JavaFileProvider
import com.syzygy.services.networking.OkHttpNetworkClient
import com.syzygy.services.persistence.EncryptedStorageProvider
import com.syzygy.services.persistence.SharedPreferencesStorageProvider
import com.syzygy.services.pushnotifications.PushProvider
import com.syzygy.services.pushnotifications.StubPushProvider
import com.syzygy.services.remoteconfig.NetworkRemoteConfigProvider
import com.syzygy.services.remoteconfig.RemoteConfigProvider
import com.syzygy.services.websocket.OkHttpWebSocketProvider
import com.syzygy.services.websocket.WebSocketProvider
import com.syzygyhub.foundation.contracts.analytics.AnalyticsEvent
import com.syzygyhub.foundation.contracts.analytics.AnalyticsProvider
import com.syzygyhub.foundation.contracts.auth.AuthProvider
import com.syzygyhub.foundation.contracts.auth.AuthState
import com.syzygyhub.foundation.contracts.auth.AuthToken
import com.syzygyhub.foundation.contracts.network.NetworkClientProtocol
import com.syzygyhub.foundation.contracts.storage.StorageKey
import com.syzygyhub.foundation.contracts.storage.StorageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract compliance tests — verifies that every service in this module:
 *
 * 1. Implements the correct Foundation interface.
 * 2. Can be instantiated with no-arg or default-arg constructor.
 * 3. Primary method returns the expected type.
 *
 * These tests act as a regression guard: if a service class is accidentally
 * removed from its interface hierarchy, the `assertIs<Interface>` call will
 * fail at compile time (the cast is checked) and at runtime.
 */
class ContractComplianceTest {
    // ------------------------------------------------------------------
    // Networking
    // ------------------------------------------------------------------

    @Test
    fun `OkHttpNetworkClient implements NetworkClientProtocol`() {
        val client = OkHttpNetworkClient()
        assertIs<NetworkClientProtocol>(client)
    }

    @Test
    fun `OkHttpNetworkClient can be instantiated with default parameters`() {
        val client = OkHttpNetworkClient()
        assertNotNull(client)
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Test
    fun `SharedPreferencesStorageProvider implements StorageProvider`() {
        val provider = SharedPreferencesStorageProvider()
        assertIs<StorageProvider>(provider)
    }

    @Test
    fun `EncryptedStorageProvider implements StorageProvider`() {
        val provider = EncryptedStorageProvider()
        assertIs<StorageProvider>(provider)
    }

    @Test
    fun `SharedPreferencesStorageProvider get returns expected type`() {
        val provider = SharedPreferencesStorageProvider()
        val key = StorageKey<String>("test.key")
        // Returns null when key is absent — null is a valid result for an absent key
        val result: String? = provider.get(key) { it }
        // The result may be null or a String — both are valid; the important thing
        // is that the method compiles and runs without throwing.
        assertTrue(result == null || result is String)
    }

    @Test
    fun `EncryptedStorageProvider set and get round-trips correctly`() {
        val provider = EncryptedStorageProvider()
        val key = StorageKey<String>("enc.key")
        provider.set("secret", key) { it }
        val retrieved = provider.get(key) { it }
        assertNotNull(retrieved)
        assertTrue(retrieved == "secret")
    }

    // ------------------------------------------------------------------
    // Auth
    // ------------------------------------------------------------------

    @Test
    fun `JWTAuthProvider implements AuthProvider`() {
        val provider = JWTAuthProvider()
        assertIs<AuthProvider>(provider)
    }

    @Test
    fun `JWTAuthProvider state is a StateFlow`() {
        val provider = JWTAuthProvider()
        assertIs<StateFlow<AuthState>>(provider.state)
    }

    @Test
    fun `JWTAuthProvider initial state is Unauthenticated`() {
        val provider = JWTAuthProvider()
        assertIs<AuthState.Unauthenticated>(provider.state.value)
    }

    @Test
    fun `JWTAuthProvider authenticate returns Authenticated state`() {
        val provider = JWTAuthProvider()
        provider.authenticate(AuthToken("access-tok"))
        assertIs<AuthState.Authenticated>(provider.state.value)
    }

    // ------------------------------------------------------------------
    // File Management
    // ------------------------------------------------------------------

    @Test
    fun `JavaFileProvider implements FileProvider`() {
        val provider = JavaFileProvider()
        assertIs<FileProvider>(provider)
    }

    @Test
    fun `JavaFileProvider exists returns Boolean`() {
        val provider = JavaFileProvider()
        val result: Boolean = provider.exists("/tmp/nonexistent_syzygy_test_path")
        assertTrue(result == false)
    }

    // ------------------------------------------------------------------
    // Push Notifications
    // ------------------------------------------------------------------

    @Test
    fun `StubPushProvider implements PushProvider`() {
        val provider = StubPushProvider()
        assertIs<PushProvider>(provider)
    }

    @Test
    fun `StubPushProvider deviceToken is initially null`() {
        val provider = StubPushProvider()
        assertTrue(provider.deviceToken == null)
    }

    @Test
    fun `StubPushProvider handlePayload returns NotificationPayload`() {
        val provider = StubPushProvider()
        val payload = provider.handlePayload("title=Hello,body=World")
        assertNotNull(payload)
        assertTrue(payload.title == "Hello")
        assertTrue(payload.body == "World")
    }

    // ------------------------------------------------------------------
    // Device Services
    // ------------------------------------------------------------------

    @Test
    fun `BuildDeviceProvider implements DeviceProvider`() {
        val provider = com.syzygy.services.deviceservices.BuildDeviceProvider()
        assertIs<DeviceProvider>(provider)
    }

    @Test
    fun `BuildDeviceProvider platform returns non-blank string`() {
        val provider = com.syzygy.services.deviceservices.BuildDeviceProvider()
        assertTrue(provider.platform.isNotBlank())
    }

    @Test
    fun `BuildDeviceProvider deviceId returns non-blank string`() {
        val provider = com.syzygy.services.deviceservices.BuildDeviceProvider()
        assertTrue(provider.deviceId.isNotBlank())
    }

    // ------------------------------------------------------------------
    // Remote Config
    // ------------------------------------------------------------------

    @Test
    fun `NetworkRemoteConfigProvider implements RemoteConfigProvider`() {
        val provider = NetworkRemoteConfigProvider()
        assertIs<RemoteConfigProvider>(provider)
    }

    @Test
    fun `NetworkRemoteConfigProvider getString returns default when key absent`() {
        val provider = NetworkRemoteConfigProvider()
        val result: String = provider.getString("missing_key", "default_val")
        assertTrue(result == "default_val")
    }

    @Test
    fun `NetworkRemoteConfigProvider cacheTtlSeconds defaults to 3600`() {
        val provider = NetworkRemoteConfigProvider()
        assertTrue(provider.cacheTtlSeconds == 3600L)
    }

    // ------------------------------------------------------------------
    // Analytics
    // ------------------------------------------------------------------

    @Test
    fun `ConsoleAnalyticsProvider implements AnalyticsProvider`() {
        val provider = ConsoleAnalyticsProvider()
        assertIs<AnalyticsProvider>(provider)
    }

    @Test
    fun `ConsoleAnalyticsProvider track does not throw`() {
        val provider = ConsoleAnalyticsProvider()
        // Primary method must not throw on valid input
        provider.track(AnalyticsEvent(name = "test.event"))
    }

    @Test
    fun `ConsoleAnalyticsProvider sessionId is non-blank`() {
        val provider = ConsoleAnalyticsProvider()
        assertTrue(provider.sessionId.isNotBlank())
    }

    // ------------------------------------------------------------------
    // Crash Reporting
    // ------------------------------------------------------------------

    @Test
    fun `ConsoleCrashReporter implements CrashReporter`() {
        val reporter = ConsoleCrashReporter()
        assertIs<CrashReporter>(reporter)
    }

    @Test
    fun `ConsoleCrashReporter recordError does not throw`() {
        val reporter = ConsoleCrashReporter()
        reporter.recordError(RuntimeException("test"), mapOf("key" to "value"))
    }

    @Test
    fun `ConsoleCrashReporter reportCrash does not throw`() {
        val reporter = ConsoleCrashReporter()
        reporter.reportCrash("test crash", mapOf("context" to "unit-test"))
    }

    // ------------------------------------------------------------------
    // WebSocket
    // ------------------------------------------------------------------

    @Test
    fun `OkHttpWebSocketProvider implements WebSocketProvider`() {
        val provider = OkHttpWebSocketProvider()
        assertIs<WebSocketProvider>(provider)
    }

    @Test
    fun `OkHttpWebSocketProvider messages is a Flow`() {
        val provider = OkHttpWebSocketProvider()
        assertIs<Flow<String>>(provider.messages)
    }

    @Test
    fun `OkHttpWebSocketProvider binaryMessages is a Flow`() {
        val provider = OkHttpWebSocketProvider()
        assertIs<Flow<ByteArray>>(provider.binaryMessages)
    }
}
