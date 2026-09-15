package com.syzygy.services.persistence

import com.syzygyhub.foundation.contracts.storage.StorageKey
import com.syzygyhub.foundation.contracts.storage.StorageProvider
import com.syzygyhub.foundation.errors.SyzygyError
import com.syzygyhub.foundation.errors.SyzygyErrorCode
import com.syzygyhub.foundation.errors.SyzygyErrorSeverity
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Thrown when a stored value cannot be deserialized to the requested type [T].
 *
 * @property code Always [SyzygyErrorCode.decodingFailed].
 * @property message Descriptive message including key name, stored raw value type, and requested type.
 * @property severity Always [SyzygyErrorSeverity.ERROR].
 * @property underlyingError The original deserialization exception.
 */
class StorageTypeMismatchError(
    override val code: SyzygyErrorCode = SyzygyErrorCode.decodingFailed,
    override val message: String,
    override val severity: SyzygyErrorSeverity = SyzygyErrorSeverity.ERROR,
    override val underlyingError: Throwable? = null,
) : Exception(message), SyzygyError

/**
 * Non-sensitive key-value store backed by an in-memory [ConcurrentHashMap].
 *
 * In production Android targets this would wrap [android.content.SharedPreferences].
 * The in-memory implementation is used here because this module targets the JVM
 * (no Android SDK) and is fully compatible with unit tests.
 */
class SharedPreferencesStorageProvider : StorageProvider {
    @PublishedApi
    internal val store = ConcurrentHashMap<String, String>()

    /**
     * Retrieves the value for [key], deserializing it from its stored string
     * form using [deserializer]. Returns [key.defaultValue] when absent.
     *
     * @throws StorageTypeMismatchError when the stored value exists but the
     *   [deserializer] throws, indicating a type mismatch or corrupt data.
     */
    override fun <T : Any> get(
        key: StorageKey<T>,
        deserializer: (String) -> T,
    ): T? {
        val raw = store[key.identifier] ?: return key.defaultValue
        return runCatching { deserializer(raw) }.getOrElse { cause ->
            val requestedType = key.defaultValue?.let { it::class.simpleName } ?: "Unknown"
            val msg =
                "StorageProvider: key \"${key.identifier}\" stores a value of type " +
                    "\"${raw::class.simpleName}\" but type \"$requestedType\" was requested"
            throw StorageTypeMismatchError(message = msg, underlyingError = cause)
        }
    }

    /**
     * Retrieves the value for [key] with a type-safe error message that names
     * the requested type [T] via a reified generic. Prefer this overload in
     * tests and application code that holds a concrete reference to
     * [SharedPreferencesStorageProvider].
     *
     * @throws StorageTypeMismatchError when the stored value exists but the
     *   [deserializer] throws, with an error message that includes both the key
     *   name and the concrete requested type name.
     */
    inline fun <reified T : Any> getTyped(
        key: StorageKey<T>,
        deserializer: (String) -> T,
    ): T? {
        val raw = store[key.identifier] ?: return key.defaultValue
        return runCatching { deserializer(raw) }.getOrElse { cause ->
            val requestedType = T::class.simpleName ?: "Unknown"
            val storedType = raw::class.simpleName ?: "Unknown"
            val msg =
                "StorageProvider: key \"${key.identifier}\" stores a value of type " +
                    "\"$storedType\" but type \"$requestedType\" was requested"
            throw StorageTypeMismatchError(message = msg, underlyingError = cause)
        }
    }

    /**
     * Stores [value] under [key], serializing it to a string with [serializer].
     */
    override fun <T : Any> set(
        value: T,
        key: StorageKey<T>,
        serializer: (T) -> String,
    ) {
        store[key.identifier] = serializer(value)
    }

    /** Removes the value stored under [key]. */
    override fun <T : Any> remove(key: StorageKey<T>) {
        store.remove(key.identifier)
    }

    /** Clears all stored values. */
    override fun clear() {
        store.clear()
    }
}

/**
 * Sensitive key-value store that encrypts values with AES-256-GCM before
 * storing them.
 *
 * In production Android targets this would wrap
 * `androidx.security.crypto.EncryptedSharedPreferences`. Because this module
 * targets the JVM (no Android SDK), encryption is performed with
 * [javax.crypto] instead — the security model is equivalent.
 *
 * @param secretKey The AES secret key used for encryption. Defaults to a
 *   freshly generated ephemeral key (safe for tests; production targets
 *   should inject a key from the Android KeyStore).
 */
class EncryptedStorageProvider(
    private val secretKey: SecretKey = generateAesKey(),
) : StorageProvider {
    @PublishedApi
    internal val store = ConcurrentHashMap<String, String>()

    /**
     * Retrieves and decrypts the value for [key], then deserializes it using
     * [deserializer]. Returns [key.defaultValue] when absent or on decryption
     * failure.
     *
     * @throws StorageTypeMismatchError when the stored value exists and
     *   decryption succeeds but the [deserializer] throws.
     */
    override fun <T : Any> get(
        key: StorageKey<T>,
        deserializer: (String) -> T,
    ): T? {
        val encrypted = store[key.identifier] ?: return key.defaultValue
        val decrypted = runCatching { decrypt(encrypted) }.getOrNull() ?: return key.defaultValue
        return runCatching { deserializer(decrypted) }.getOrElse { cause ->
            val msg =
                "StorageProvider: cannot deserialize value for key '${key.identifier}'. " +
                    "Stored raw type is ${decrypted::class.simpleName}, " +
                    "requested type could not be produced by the deserializer. " +
                    "Cause: ${cause.message}"
            throw StorageTypeMismatchError(message = msg, underlyingError = cause)
        }
    }

    /**
     * Retrieves and decrypts the value for [key] with a type-safe error message
     * that names the requested type [T] via a reified generic. Prefer this
     * overload in tests and application code that holds a concrete reference to
     * [EncryptedStorageProvider].
     *
     * @throws StorageTypeMismatchError when the stored value exists, decryption
     *   succeeds, but the [deserializer] throws, with an error message that
     *   includes both the key name and the concrete requested type name.
     */
    inline fun <reified T : Any> getTyped(
        key: StorageKey<T>,
        deserializer: (String) -> T,
    ): T? {
        val encrypted = store[key.identifier] ?: return key.defaultValue
        val decrypted = runCatching { decrypt(encrypted) }.getOrNull() ?: return key.defaultValue
        return runCatching { deserializer(decrypted) }.getOrElse { cause ->
            val requestedType = T::class.simpleName ?: "Unknown"
            val storedType = decrypted::class.simpleName ?: "Unknown"
            val msg =
                "StorageProvider: key \"${key.identifier}\" stores a value of type " +
                    "\"$storedType\" but type \"$requestedType\" was requested"
            throw StorageTypeMismatchError(message = msg, underlyingError = cause)
        }
    }

    /**
     * Encrypts [value] (after serialization with [serializer]) and stores it
     * under [key].
     */
    override fun <T : Any> set(
        value: T,
        key: StorageKey<T>,
        serializer: (T) -> String,
    ) {
        store[key.identifier] = encrypt(serializer(value))
    }

    /** Removes the encrypted value stored under [key]. */
    override fun <T : Any> remove(key: StorageKey<T>) {
        store.remove(key.identifier)
    }

    /** Clears all stored encrypted values. */
    override fun clear() {
        store.clear()
    }

    // ------------------------------------------------------------------
    // AES-256-GCM helpers
    // ------------------------------------------------------------------

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Store as base64(iv) + ":" + base64(ciphertext)
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(iv)}:${enc.encodeToString(ciphertext)}"
    }

    @PublishedApi
    internal fun decrypt(encoded: String): String {
        val parts = encoded.split(":")
        require(parts.size == 2) { "Invalid ciphertext format" }
        val dec = Base64.getDecoder()
        val iv = dec.decode(parts[0])
        val ciphertext = dec.decode(parts[1])
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        /** Generates a fresh AES-256 key. */
        fun generateAesKey(): SecretKey {
            val gen = KeyGenerator.getInstance("AES")
            gen.init(256)
            return gen.generateKey()
        }
    }
}
