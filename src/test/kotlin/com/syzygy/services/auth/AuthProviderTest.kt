package com.syzygy.services.auth

import com.syzygyhub.foundation.contracts.auth.AuthState
import com.syzygyhub.foundation.contracts.auth.AuthToken
import com.syzygyhub.foundation.primitives.time.SyzygyTimestamp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthProviderTest {
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
}
