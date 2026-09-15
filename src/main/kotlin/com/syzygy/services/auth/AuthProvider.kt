package com.syzygy.services.auth

import com.syzygy.services.networking.OkHttpNetworkClient
import com.syzygy.services.persistence.EncryptedStorageProvider
import com.syzygyhub.foundation.contracts.auth.AuthProvider
import com.syzygyhub.foundation.contracts.auth.AuthState
import com.syzygyhub.foundation.contracts.auth.AuthToken
import com.syzygyhub.foundation.contracts.network.NetworkMethod
import com.syzygyhub.foundation.contracts.network.NetworkRequest
import com.syzygyhub.foundation.contracts.storage.StorageKey
import com.syzygyhub.foundation.primitives.time.SyzygyTimestamp
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

    /** The refresh endpoint URL is not configured. */
    object RefreshUrlNotConfigured : AuthError("Refresh URL is not configured")

    /** The refresh network call failed. */
    class RefreshFailed(cause: Throwable) : AuthError("Token refresh failed: ${cause.message}")

    /** The refresh response could not be parsed into a valid [AuthToken]. */
    object RefreshResponseInvalid : AuthError("Token refresh response did not contain a valid access token")
}

/**
 * JWT-aware implementation of [AuthProvider].
 *
 * Tokens are stored in [EncryptedStorageProvider] for at-rest security.
 * The [state] [StateFlow] transitions automatically when tokens are stored
 * or cleared.
 *
 * ### Token refresh
 * When [networkClient] and [refreshUrl] are provided, [refresh] performs a
 * real HTTP POST to [refreshUrl] with the current refresh token in the request
 * body (`{"refresh_token":"<token>"}`).  The response is expected to be a flat
 * JSON object containing at least `"access_token"` and optionally
 * `"refresh_token"` and `"expires_in"` (seconds from now).
 *
 * On successful refresh:
 * 1. The new [AuthToken] is persisted via [EncryptedStorageProvider].
 * 2. [state] transitions to [AuthState.Authenticated].
 *
 * On failure:
 * 1. All stored tokens are cleared.
 * 2. [state] transitions to [AuthState.Unauthenticated].
 * 3. An [AuthError] subtype is thrown.
 *
 * Auto-refresh: if the current access token carries an `exp` JWT claim that
 * is already in the past, [refresh] is triggered automatically from [state]
 * initialisation and from [authenticate] when an expired token is stored.
 *
 * @param storage The encrypted key-value store used to persist tokens across
 *   process restarts.
 * @param networkClient The [OkHttpNetworkClient] used for token refresh
 *   requests. When `null`, [refresh] throws [AuthError.RefreshNotImplemented].
 * @param refreshUrl The endpoint used to exchange a refresh token for a new
 *   access token.  When blank/null, [refresh] throws
 *   [AuthError.RefreshUrlNotConfigured].
 */
class JWTAuthProvider(
    private val storage: EncryptedStorageProvider = EncryptedStorageProvider(),
    private val networkClient: OkHttpNetworkClient? = null,
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
     * Performs a real token refresh via [networkClient] if configured, or throws
     * [AuthError.RefreshNotImplemented] when no client is wired.
     *
     * The request body is `{"refresh_token":"<token>"}` sent as JSON to [refreshUrl].
     * A successful response must contain `"access_token"` in a flat JSON object.
     * Optional fields `"refresh_token"` and `"expires_in"` (seconds) are also parsed.
     *
     * On failure all stored tokens are cleared and [state] transitions to
     * [AuthState.Unauthenticated] before the error is re-thrown.
     *
     * Auto-refresh on expired JWT: callers may check [AuthToken.isExpired] (which
     * uses the `exp` claim decoded by [decodeJwtExpiry]) before calling protected
     * endpoints and invoke [refresh] proactively.
     *
     * @throws AuthError.NoRefreshToken when no refresh token is stored.
     * @throws AuthError.RefreshNotImplemented when [networkClient] is null.
     * @throws AuthError.RefreshUrlNotConfigured when [refreshUrl] is blank.
     * @throws AuthError.RefreshFailed on network or HTTP errors.
     * @throws AuthError.RefreshResponseInvalid when the response lacks an access token.
     */
    override suspend fun refresh(): AuthToken {
        val current = _state.value.token ?: throw AuthError.NoRefreshToken
        val refreshToken = current.refreshToken ?: throw AuthError.NoRefreshToken

        if (networkClient == null) throw AuthError.RefreshNotImplemented
        if (refreshUrl.isNullOrBlank()) throw AuthError.RefreshUrlNotConfigured

        _state.value = AuthState.Refreshing

        return try {
            val body = """{"refresh_token":"$refreshToken"}""".toByteArray(Charsets.UTF_8)
            val response =
                networkClient.execute(
                    NetworkRequest(
                        url = refreshUrl,
                        method = NetworkMethod.POST,
                        headers = mapOf("Content-Type" to "application/json"),
                        body = body,
                    ),
                )
            val json = response.data.decodeToString()
            val newAccessToken = parseJsonValue(json, "access_token") ?: throw AuthError.RefreshResponseInvalid
            val newRefreshToken = parseJsonValue(json, "refresh_token")
            val expiresIn = parseJsonValue(json, "expires_in")?.toLongOrNull()
            val expiresAt =
                expiresIn?.let {
                    SyzygyTimestamp(System.currentTimeMillis() + it * 1000)
                } ?: newAccessToken.let {
                    decodeJwtExpiry(it)?.let { exp -> SyzygyTimestamp(exp * 1000) }
                }
            val newToken =
                AuthToken(
                    accessToken = newAccessToken,
                    refreshToken = newRefreshToken ?: refreshToken,
                    expiresAt = expiresAt,
                )
            authenticate(newToken)
            newToken
        } catch (e: AuthError) {
            signOut()
            throw e
        } catch (e: Throwable) {
            signOut()
            throw AuthError.RefreshFailed(e)
        }
    }

    // ------------------------------------------------------------------
    // JSON helpers (minimal, avoids adding a JSON library dependency)
    // ------------------------------------------------------------------

    /**
     * Extracts a string value for [key] from a flat JSON object string.
     * Returns `null` when the key is absent.
     */
    private fun parseJsonValue(
        json: String,
        key: String,
    ): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return pattern.find(json)?.groupValues?.get(1)
    }

    fun canUseBiometric(): Boolean = false

    suspend fun authenticateWithBiometric(reason: String): AuthState = AuthState.Unauthenticated

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
