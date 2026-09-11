package com.syzygy.services.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthProviderTest {
    @Test
    fun `JWTAuthProvider stores and clears token`() {
        val provider = JWTAuthProvider()
        assertNull(provider.accessToken)
        provider.storeToken("tok123")
        assertEquals("tok123", provider.accessToken)
        provider.clearToken()
        assertNull(provider.accessToken)
    }
}
