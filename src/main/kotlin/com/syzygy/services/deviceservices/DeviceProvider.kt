package com.syzygy.services.deviceservices

/**
 * Defines the contract for accessing device information.
 */
interface DeviceProvider {
    /** The device manufacturer (e.g. "Google"). */
    val manufacturer: String

    /** The device model (e.g. "Pixel 8"). */
    val model: String

    /** The Android OS version string (e.g. "14"). */
    val osVersion: String
}

/**
 * A [DeviceProvider] backed by [android.os.Build] accessed via reflection.
 * On non-Android JVM targets (tests) the reflection calls fail gracefully and return "unknown".
 */
class BuildDeviceProvider : DeviceProvider {
    override val manufacturer: String
        get() =
            runCatching {
                Class.forName("android.os.Build").getField("MANUFACTURER").get(null) as String
            }.getOrDefault("unknown")

    override val model: String
        get() =
            runCatching {
                Class.forName("android.os.Build").getField("MODEL").get(null) as String
            }.getOrDefault("unknown")

    override val osVersion: String
        get() =
            runCatching {
                val versionClass = Class.forName("android.os.Build\$VERSION")
                versionClass.getField("RELEASE").get(null) as String
            }.getOrDefault("unknown")
}
