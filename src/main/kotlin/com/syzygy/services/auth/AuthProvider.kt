package com.syzygy.services.auth

import com.syzygy.services.networking.OkHttpNetworkClient
import com.syzygy.services.persistence.EncryptedStorageProvider
import com.syzygyhub.foundation.contracts.auth.AuthProvider
import com.syzygyhub.foundation.contracts.auth.AuthState
import com.syzygyhub.foundation.contracts.auth.AuthToken
import com.syzygyhub.foundation.contracts.storage.StorageKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

// ------------------------------------------------------------------
// Storage keys for JWT credential persistence
// ------------------------------------------------------------------

private val ACCESS_TOKEN_KEY = StorageKey<String>("auth.access_token")
private val REFRESH_TOKEN_KEY = StorageKey<String>("auth.refresh_token")
private val EXPIRES_AT_KEY = StorageKey<Long>("auth.expires_at")

/**
 * Decodes the `exp` claim (Unix epoch seconds) from the payload of a JWT
 * without verifying the signature.
 *
 * Returns `null` when the token is malformed or the claim is absent.
 */
internal fun decodeJwtExpiry(jwt: String): Long? =
    runCatching {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        val payload = parts[1]
        // JWT uses URL-safe Base64 without padding
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val json = String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        val match = Regex(""""exp"\s*:\s*(\d+)""").find(json)
        match?.groupValues?.get(1)?.toLong()
    }.getOrNull()

/**
 * Errors produced by [JWTAuthProvider].
 */
sealed class AuthError(message: String) : Exception(message) {
    /** No refresh token is available to perform a refresh. */
    object NoRefreshToken : AuthError("No refresh token available")

    /** The token refresh network call is not yet wired to a back-end. */
    object RefreshNotImplemented : AuthError("Token refresh stub — wire to a real endpoint")
}

/**
 * JWT-aware implementation of [AuthProvider].
 *
 * Tokens are stored in [EncryptedStorageProvider] for at-rest security.
 * The [state] [StateFlow] transitions automatically when tokens are stored
 * or cleared.
 *
 * @param storage The encrypted key-value store used to persist tokens across
 *   process restarts.
 * @param networkClient The [OkHttpNetworkClient] used for token refresh
 *   requests (stub — supply a real implementation and [refreshUrl]).
 * @param refreshUrl The endpoint used to exchange a refresh token for a new
 *   access token.
 */
class JWTAuthProvider(
    private val storage: EncryptedStorageProvider = EncryptedStorageProvider(),
    @Suppress("UnusedPrivateMember")
    private val networkClient: OkHttpNetworkClient? = null,
    @Suppress("UnusedPrivateMember")
    private val refreshUrl: String? = null,
) : AuthProvider {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    /** Hot stream of the current [AuthState]. Emits the latest state immediately on collection. */
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // Restore persisted token on initialisation
        val accessToken = storage.get(ACCESS_TOKEN_KEY) { it }
        val refreshToken = storage.get(REFRESH_TOKEN_KEY) { it }
        val expiresAt = storage.get(EXPIRES_AT_KEY) { it.toLong() }
        if (accessToken != null) {
            val token =
                AuthToken(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt =
                        expiresAt?.let {
                            com.syzygyhub.foundation.primitives.time.SyzygyTimestamp(it)
                        },
                )
            _state.value =
                if (token.isExpired) AuthState.Expired(token) else AuthState.Authenticated(token)
        }
    }

    /**
     * Stores [token] in encrypted storage and transitions [state] to
     * [AuthState.Authenticated].
     */
    override fun authenticate(token: AuthToken) {
        storage.set(token.accessToken, ACCESS_TOKEN_KEY) { it }
        token.refreshToken?.let { storage.set(it, REFRESH_TOKEN_KEY) { v -> v } }
        token.expiresAt?.let { storage.set(it.millisecondsSinceEpoch, EXPIRES_AT_KEY) { v -> v.toString() } }
        _state.value = AuthState.Authenticated(token)
    }

    /**
     * Stub token refresh. Transitions to [AuthState.Refreshing] during the
     * attempt and throws [AuthError.RefreshNotImplemented] — wire [networkClient]
     * and [refreshUrl] to enable real refresh.
     *
     * @throws AuthError.NoRefreshToken when no refresh token is available.
     * @throws AuthError.RefreshNotImplemented always (stub).
     */
    override suspend fun refresh(): AuthToken {
        val current = _state.value.token ?: throw AuthError.NoRefreshToken
        if (current.refreshToken == null) throw AuthError.NoRefreshToken
        _state.value = AuthState.Refreshing
        // TODO: POST to refreshUrl with refreshToken; parse response; call authenticate()
        _state.value = AuthState.Expired(current)
        throw AuthError.RefreshNotImplemented
    }

    /**
     * Clears all stored credentials and transitions [state] to
     * [AuthState.Unauthenticated].
     */
    override fun signOut() {
        storage.remove(ACCESS_TOKEN_KEY)
        storage.remove(REFRESH_TOKEN_KEY)
        storage.remove(EXPIRES_AT_KEY)
        _state.value = AuthState.Unauthenticated
    }
}
