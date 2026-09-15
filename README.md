![CI](https://github.com/Syzygy-Hub/syzygy-services-android/actions/workflows/ci.yml/badge.svg)
![Version](https://img.shields.io/badge/version-1.1.0-blue)
![Android](https://img.shields.io/badge/platform-Android%20%7C%20Kotlin-green)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Syzygy-Hub/.github/main/brand/assets/banners/syzygy-banner-dark-1200.png">
  <img src="https://raw.githubusercontent.com/Syzygy-Hub/.github/main/brand/assets/banners/syzygy-banner-light-1200.png" alt="Syzygy" width="600">
</picture>

# syzygy-services-android

Concrete I/O service implementations for the Syzygy Android ecosystem — networking, persistence, auth, file management, push notifications, device services, remote config, analytics, crash reporting, and WebSocket.

## Modules

| Module | Package | Interface | Implementation |
|--------|---------|-----------|----------------|
| Networking | `com.syzygy.services.networking` | `NetworkClientProtocol` | `OkHttpNetworkClient` |
| Persistence | `com.syzygy.services.persistence` | `StorageProvider` | `SharedPreferencesStorageProvider` |
| Auth | `com.syzygy.services.auth` | `AuthProvider` | `JWTAuthProvider` |
| File Management | `com.syzygy.services.filemanagement` | `FileProvider` | `JavaFileProvider` |
| Push Notifications | `com.syzygy.services.pushnotifications` | `PushProvider` | `StubPushProvider` |
| Device Services | `com.syzygy.services.deviceservices` | `DeviceProvider` | `BuildDeviceProvider` |
| Remote Config | `com.syzygy.services.remoteconfig` | `RemoteConfigProvider` | `NetworkRemoteConfigProvider` |
| Analytics | `com.syzygy.services.analytics` | `AnalyticsProvider` | `ConsoleAnalyticsProvider` |
| Crash Reporting | `com.syzygy.services.crashreporting` | `CrashReporter` | `ConsoleCrashReporter` |
| WebSocket | `com.syzygy.services.websocket` | `WebSocketProvider` | `OkHttpWebSocketProvider` |

## Installation

**Step 1:** Add JitPack to `settings.gradle.kts`:

```kotlin
maven { url = uri("https://jitpack.io") }
```

**Step 2:** Add the dependency to `build.gradle.kts`:

```kotlin
implementation("com.github.Syzygy-Hub:syzygy-services-android:1.1.0")
```

## Requirements

- Android API 26+
- Kotlin 2.0+
- JDK 17+

## Dependencies

- `syzygy-foundation-android` 1.1.0

## Ecosystem

`syzygy-services-android` is the I/O layer of the Syzygy Android ecosystem. It depends exclusively on `syzygy-foundation-android` and is consumed by higher-level layers. Each module defines its own protocol (interface) alongside its concrete implementation, enabling easy substitution in tests and production.

## Push Notifications

The `com.syzygy.services.pushnotifications` module provides a pure-JVM `PushProvider` interface and `StubPushProvider` for testing. Real Firebase Cloud Messaging (FCM) integration lives in the consuming Android **app** module, not this library, so the library remains testable without Android instrumentation.

### FCM Integration Steps

**1. Add the dependency** to your **app** module's `build.gradle.kts`:
```kotlin
implementation("com.google.firebase:firebase-messaging:23.x.x")
```

**2. Declare the service** in `AndroidManifest.xml`:
```xml
<service
    android:name=".MyFirebaseMessagingService"
    android:exported="false">
  <intent-filter>
    <action android:name="com.google.firebase.MESSAGING_EVENT" />
  </intent-filter>
</service>
```

**3. Implement `FirebaseMessagingService`**:
```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        pushProvider.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val json = buildString {
            append("{")
            message.notification?.let {
                append(""""title":"${it.title}","body":"${it.body}",""")
            }
            message.data.entries.joinTo(this) { (k, v) -> """"$k":"$v"""" }
            append("}")
        }
        val payload = pushProvider.handlePayload(json)
        // Show a notification using NotificationCompat.Builder.
    }
}
```

**4. Request `POST_NOTIFICATIONS` permission** (Android 13+):
```kotlin
ActivityCompat.requestPermissions(
    activity,
    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
    REQUEST_CODE,
)
```

**5. Retrieve the initial token** on first launch:
```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) pushProvider.registerToken(task.result)
}
```

### Payload Factory Helpers

`NotificationPayload` provides two factory helpers:

- **`NotificationPayload.build { ... }`** — DSL builder for constructing payloads in tests or app code:
  ```kotlin
  val payload = NotificationPayload.build(title = "Alert", body = "Server down") {
      data("severity", "critical")
  }
  ```
- **`NotificationPayload.fromJson(json)`** — parses a flat JSON string (e.g. serialised from `RemoteMessage.data`) into a `NotificationPayload`.

## License

MIT
