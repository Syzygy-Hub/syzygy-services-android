![CI](https://github.com/Syzygy-Hub/syzygy-services-android/actions/workflows/ci.yml/badge.svg)
![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Android](https://img.shields.io/badge/platform-Android%20%7C%20Kotlin-green)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Syzygy-Hub/.github/main/assets/syzygy-banner-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/Syzygy-Hub/.github/main/assets/syzygy-banner-light.png">
  <img alt="Syzygy" src="https://raw.githubusercontent.com/Syzygy-Hub/.github/main/assets/syzygy-banner-light.png">
</picture>

# syzygy-services-android

Concrete I/O service implementations for the Syzygy Android ecosystem — networking, persistence, auth, file management, push notifications, device services, remote config, analytics, crash reporting, and WebSocket.

## Modules

| Module | Package | Interface | Implementation |
|--------|---------|-----------|----------------|
| Networking | `com.syzygy.services.networking` | `NetworkClient` | `CoroutineNetworkClient` |
| Persistence | `com.syzygy.services.persistence` | `StorageProvider` | `InMemoryStorageProvider` |
| Auth | `com.syzygy.services.auth` | `AuthProvider` | `JWTAuthProvider` |
| File Management | `com.syzygy.services.filemanagement` | `FileProvider` | `JavaFileProvider` |
| Push Notifications | `com.syzygy.services.pushnotifications` | `PushProvider` | `FCMPushProvider` |
| Device Services | `com.syzygy.services.deviceservices` | `DeviceProvider` | `BuildDeviceProvider` |
| Remote Config | `com.syzygy.services.remoteconfig` | `RemoteConfigProvider` | `InMemoryRemoteConfigProvider` |
| Analytics | `com.syzygy.services.analytics` | `AnalyticsProvider` | `ConsoleAnalyticsProvider` |
| Crash Reporting | `com.syzygy.services.crashreporting` | `CrashReporter` | `ConsoleCrashReporter` |
| WebSocket | `com.syzygy.services.websocket` | `WebSocketProvider` | `StubWebSocketProvider` |

## Installation

**Step 1:** Add JitPack to `settings.gradle.kts`:

```kotlin
maven { url = uri("https://jitpack.io") }
```

**Step 2:** Add the dependency to `build.gradle.kts`:

```kotlin
implementation("com.github.Syzygy-Hub:syzygy-services-android:1.0.0")
```

## Requirements

- Android API 26+
- Kotlin 2.0+
- JDK 17+

## Dependencies

- `syzygy-foundation-android` 1.1.0

## Ecosystem

`syzygy-services-android` is the I/O layer of the Syzygy Android ecosystem. It depends exclusively on `syzygy-foundation-android` and is consumed by higher-level layers. Each module defines its own protocol (interface) alongside its concrete implementation, enabling easy substitution in tests and production.

## License

MIT
