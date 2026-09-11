package com.syzygy.services.auth

/**
 * Defines the contract for authentication and token management.
 */
interface AuthProvider {
    /** The current access token, or null if unauthenticated. */
    val accessToken: String?

    /** Stores the given [token] as the current access token. */
    fun storeToken(token: String)

    /** Clears the current access token. */
    fun clearToken()

    /** Refreshes the access token. Returns the new token or throws on failure. */
    suspend fun refreshToken(): String
}

/** Errors thrown by [JWTAuthProvider]. */
sealed class AuthError(message: String) : Exception(message) {
    /** Token refresh is not yet implemented. */
    object RefreshNotImplemented : AuthError("Token refresh not implemented")
}

/**
 * An [AuthProvider] that stores a JWT token in memory with a stub refresh.
 */
class JWTAuthProvider(token: String? = null) : AuthProvider {
    private var _accessToken: String? = token

    override val accessToken: String? get() = _accessToken

    override fun storeToken(token: String) {
        _accessToken = token
    }

    override fun clearToken() {
        _accessToken = null
    }

    override suspend fun refreshToken(): String {
        throw AuthError.RefreshNotImplemented
    }
}
