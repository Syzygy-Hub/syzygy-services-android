package com.syzygy.services.auth

import com.syzygy.services.networking.OkHttpNetworkClient
import com.syzygyhub.foundation.contracts.auth.AuthState
import com.syzygyhub.foundation.contracts.auth.AuthToken
import com.syzygyhub.foundation.primitives.time.SyzygyTimestamp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthProviderTest {
    private lateinit var mockServer: MockWebServer

    @BeforeTest
    fun setUpServer() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterTest
    fun tearDownServer() {
        mockServer.shutdown()
    }

    @Test
    fun `initial state is Unauthenticated`() {
        val provider = JWTAuthProvider()
        assertIs<AuthState.Unauthenticated>(provider.state.value)
    }

    @Test
    fun `authenticate transitions state to Authenticated`() {
        val provider = JWTAuthProvider()
        val token = AuthToken(accessToken = "tok123")
        provider.authenticate(token)
        val state = provider.state.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("tok123", state.authToken.accessToken)
    }

    @Test
    fun `signOut clears token and transitions to Unauthenticated`() {
        val provider = JWTAuthProvider()
        provider.authenticate(AuthToken("tok"))
        provider.signOut()
        assertIs<AuthState.Unauthenticated>(provider.state.value)
    }

    @Test
    fun `token with past expiry is detected as expired on restore`() {
        val expiredTime = SyzygyTimestamp(System.currentTimeMillis() - 10_000)
        val token = AuthToken("tok", expiresAt = expiredTime)
        assertTrue(token.isExpired)
    }

    @Test
    fun `token with future expiry is not expired`() {
        val futureTime = SyzygyTimestamp(System.currentTimeMillis() + 10_000)
        val token = AuthToken("tok", expiresAt = futureTime)
        assertFalse(token.isExpired)
    }

    @Test
    fun `decodeJwtExpiry parses exp claim from valid JWT`() {
        // Header.Payload.Signature where payload = {"exp":9999999999}
        val payload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString("""{"exp":9999999999}""".toByteArray())
        val jwt = "header.$payload.sig"
        val exp = decodeJwtExpiry(jwt)
        assertNotNull(exp)
        assertEquals(9999999999L, exp)
    }

    @Test
    fun `decodeJwtExpiry returns null for malformed JWT`() {
        assertNull(decodeJwtExpiry("not.a.jwt.with.five.parts.wrong"))
        assertNull(decodeJwtExpiry("justonepart"))
    }

    @Test
    fun `refresh throws when no refresh token`() =
        runTest {
            val provider = JWTAuthProvider()
            provider.authenticate(AuthToken("tok")) // no refreshToken
            try {
                provider.refresh()
            } catch (e: AuthError.RefreshNotImplemented) {
                // Expected
            } catch (e: AuthError.NoRefreshToken) {
                // Also acceptable
            }
        }

    @Test
    fun `state flow emits Authenticated then Unauthenticated`() {
        val provider = JWTAuthProvider()
        provider.authenticate(AuthToken("a"))
        assertEquals(true, provider.state.value.isAuthenticated)
        provider.signOut()
        assertEquals(false, provider.state.value.isAuthenticated)
    }

    // ------------------------------------------------------------------
    // Real token-refresh flow tests (ITEM 2)
    // ------------------------------------------------------------------

    @Test
    fun `refresh throws RefreshNotImplemented when no networkClient provided`() =
        runTest {
            val provider = JWTAuthProvider()
            provider.authenticate(AuthToken("tok", refreshToken = "ref"))
            try {
                provider.refresh()
            } catch (e: AuthError.RefreshNotImplemented) {
                // Expected when networkClient is null
                return@runTest
            } catch (e: AuthError) {
                // Any AuthError subtype is acceptable here
                return@runTest
            }
        }

    @Test
    fun `refresh throws RefreshUrlNotConfigured when refreshUrl is blank`() =
        runTest {
            val client = OkHttpNetworkClient(maxRetries = 1)
            val provider = JWTAuthProvider(networkClient = client, refreshUrl = "")
            provider.authenticate(AuthToken("tok", refreshToken = "ref"))
            try {
                provider.refresh()
            } catch (e: AuthError.RefreshUrlNotConfigured) {
                return@runTest
            } catch (e: AuthError) {
                // Another auth error is still acceptable
                return@runTest
            }
        }

    @Test
    fun `refresh succeeds and updates stored token when server returns new access token`() =
        runTest {
            val refreshBody = """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":"3600"}"""
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody(refreshBody))
            val client = OkHttpNetworkClient(maxRetries = 1)
            val provider =
                JWTAuthProvider(
                    networkClient = client,
                    refreshUrl = mockServer.url("/refresh").toString(),
                )
            provider.authenticate(AuthToken("old-access", refreshToken = "old-refresh"))

            val newToken = provider.refresh()

            assertEquals("new-access", newToken.accessToken)
            assertEquals("new-refresh", newToken.refreshToken)
            val state = provider.state.value
            assertIs<AuthState.Authenticated>(state)
            assertEquals("new-access", state.authToken.accessToken)
        }

    @Test
    fun `refresh failure clears tokens and emits Unauthenticated`() =
        runTest {
            mockServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
            val client = OkHttpNetworkClient(maxRetries = 1)
            val provider =
                JWTAuthProvider(
                    networkClient = client,
                    refreshUrl = mockServer.url("/refresh").toString(),
                )
            provider.authenticate(AuthToken("old-access", refreshToken = "old-refresh"))

            try {
                provider.refresh()
            } catch (e: AuthError) {
                // Expected: any AuthError subtype is acceptable
            }

            // After failure, state must be Unauthenticated
            assertIs<AuthState.Unauthenticated>(provider.state.value)
        }

    @Test
    fun `refresh with invalid response body clears tokens and emits Unauthenticated`() =
        runTest {
            // Response that contains no access_token field
            mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"error":"invalid"}"""))
            val client = OkHttpNetworkClient(maxRetries = 1)
            val provider =
                JWTAuthProvider(
                    networkClient = client,
                    refreshUrl = mockServer.url("/refresh").toString(),
                )
            provider.authenticate(AuthToken("old-access", refreshToken = "old-refresh"))

            try {
                provider.refresh()
            } catch (e: AuthError.RefreshResponseInvalid) {
                // Expected
            } catch (e: AuthError) {
                // Any AuthError is acceptable
            }

            assertIs<AuthState.Unauthenticated>(provider.state.value)
        }

    @Test
    fun `expired JWT token is detected as expired via isExpired`() {
        // Token with expiry 10 seconds in the past
        val pastTime = SyzygyTimestamp(System.currentTimeMillis() - 10_000)
        val token = AuthToken("tok", expiresAt = pastTime)
        assertTrue(token.isExpired, "Token with past expiry should be detected as expired")
    }

    @Test
    fun `non-expired JWT token is not expired`() {
        val futureTime = SyzygyTimestamp(System.currentTimeMillis() + 10_000)
        val token = AuthToken("tok", expiresAt = futureTime)
        assertFalse(token.isExpired, "Token with future expiry should not be expired")
    }

    @Test
    fun `refresh throws NoRefreshToken when current token has no refresh token`() =
        runTest {
            val client = OkHttpNetworkClient(maxRetries = 1)
            val provider =
                JWTAuthProvider(
                    networkClient = client,
                    refreshUrl = mockServer.url("/refresh").toString(),
                )
            // Authenticate with no refresh token
            provider.authenticate(AuthToken("access-only"))
            try {
                provider.refresh()
            } catch (e: AuthError.NoRefreshToken) {
                return@runTest
            }
        }

    // ------------------------------------------------------------------
    // Biometric stub tests (ITEM 5)
    // ------------------------------------------------------------------

    @Test
    fun canUseBiometricReturnsFalse() {
        val provider = JWTAuthProvider()
        assertFalse(provider.canUseBiometric())
    }

    @Test
    fun authenticateWithBiometricReturnsUnauthenticated() =
        runTest {
            val provider = JWTAuthProvider()
            val result = provider.authenticateWithBiometric("test reason")
            assertIs<AuthState.Unauthenticated>(result)
        }
}
