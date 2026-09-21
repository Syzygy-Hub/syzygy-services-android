package com.syzygy.services.internal

internal object RedactionPolicy {
    private val redactedKeys =
        setOf(
            "email",
            "userid",
            "user_id",
            "uid",
            "traits",
            "token",
            "password",
            "secret",
        )

    fun redact(
        key: String,
        value: String,
    ): String = if (key.lowercase() in redactedKeys) "<redacted>" else value

    fun redactMap(map: Map<String, String>): Map<String, String> = map.mapValues { (key, value) -> redact(key, value) }

    fun redactAny(
        key: String,
        value: Any?,
    ): Any? = if (key.lowercase() in redactedKeys) "<redacted>" else value
}
