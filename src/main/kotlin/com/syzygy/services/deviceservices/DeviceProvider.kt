package com.syzygy.services.deviceservices

import com.syzygy.services.persistence.SharedPreferencesStorageProvider
import com.syzygyhub.foundation.contracts.storage.StorageKey
import java.util.UUID

private val DEVICE_ID_KEY = StorageKey<String>("device.id")

/**
 * Contract for accessing device-level metadata.
 */
interface DeviceProvider {
    /**
     * A stable, unique identifier for this device installation.
     *
     * The ID persists across app launches and is generated once on first
     * access. It is **not** the Android `ANDROID_ID` — it is a random UUID
     * stored in [SharedPreferences][com.syzygy.services.persistence.SharedPreferencesStorageProvider].
     */
    val deviceId: String

    /**
     * The platform identifier.
     *
     * Always `"android"` on Android targets; may return `"jvm"` in pure
     * JVM test environments.
     */
    val platform: String

    /**
     * The OS release version string (e.g. `"14"` for Android 14).
     *
     * Obtained via reflection on `android.os.Build.VERSION.RELEASE`; falls
     * back to the JVM `os.version` system property in non-Android
     * environments.
     */
    val osVersion: String

    /**
     * The host application's version name (e.g. `"2.3.1"`).
     *
     * Requires an Android [android.content.Context] to read from the
     * `PackageManager`; returns `"unknown"` in stub/test environments.
     */
    val appVersion: String

    /**
     * `true` when the device is a software emulator rather than physical
     * hardware.
     *
     * Detected via `Build.FINGERPRINT` containing `"generic"` on Android;
     * always `false` in pure JVM environments.
     */
    val isEmulator: Boolean
}

/**
 * [DeviceProvider] implementation that reads hardware metadata via reflection
 * (for compatibility with the pure-JVM build target) and persists [deviceId]
 * using [SharedPreferencesStorageProvider].
 *
 * @param storage The storage provider used to persist the device identifier.
 */
class BuildDeviceProvider(
    private val storage: SharedPreferencesStorageProvider = SharedPreferencesStorageProvider(),
) : DeviceProvider {
    /**
     * Reads or generates a stable device UUID, persisting it so that the
     * same ID is returned on subsequent calls.
     */
    override val deviceId: String
        get() {
            val existing = storage.get(DEVICE_ID_KEY) { it }
            if (existing != null) return existing
            val newId = UUID.randomUUID().toString()
            storage.set(newId, DEVICE_ID_KEY) { it }
            return newId
        }

    /** Always `"android"` on Android targets; `"jvm"` in pure JVM environments. */
    override val platform: String
        get() {
            val onAndroid =
                runCatching {
                    Class.forName("android.os.Build")
                    true
                }.getOrDefault(false)
            return if (onAndroid) "android" else "jvm"
        }

    /**
     * Returns `android.os.Build.VERSION.RELEASE` via reflection, falling
     * back to the JVM `os.version` property.
     */
    override val osVersion: String
        get() =
            runCatching {
                val versionClass = Class.forName("android.os.Build\$VERSION")
                versionClass.getField("RELEASE").get(null) as String
            }.getOrElse { System.getProperty("os.version") ?: "unknown" }

    /**
     * Returns `"unknown"` in stub environments; real Android targets would
     * read from `PackageManager`.
     */
    override val appVersion: String
        get() =
            runCatching {
                // Would use context.packageManager.getPackageInfo(...).versionName on Android
                System.getProperty("app.version") ?: "unknown"
            }.getOrDefault("unknown")

    /**
     * Detects an emulator by checking whether `Build.FINGERPRINT` contains
     * `"generic"`. Returns `false` in pure JVM environments.
     */
    override val isEmulator: Boolean
        get() =
            runCatching {
                val fingerprint =
                    Class.forName("android.os.Build").getField("FINGERPRINT").get(null) as String
                fingerprint.contains("generic", ignoreCase = true)
            }.getOrDefault(false)
}
