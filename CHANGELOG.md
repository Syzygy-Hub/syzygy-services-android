# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-09-13

### Added

- Networking retry integration tests using MockWebServer (RetryIntegrationTest) — covers retry count, exponential backoff, recovery on Nth attempt, max retry ceiling
- Auth: Real token-refresh flow — HTTP POST to configurable `refreshUrl`, auto-refresh on expired JWT `exp` claim, refresh result updates stored token, failure clears tokens and emits `AuthState.Unauthenticated`
- RemoteConfig: Cache TTL — `cacheTtlSeconds` parameter (default 3600) on `NetworkRemoteConfigProvider`; `fetch()` returns cached data within TTL, hits network only when stale or empty
- WebSocket binary send tests — send `ByteArray` binary, receive binary via callback, mixed text+binary scenarios; `binaryMessages` Flow exposed on `WebSocketProvider` for reactive binary frame consumption
- BackoffClock interface added to main source; OkHttpNetworkClient constructor accepts optional `backoffClock` parameter for deterministic retry testing
- PushNotifications documentation — KDoc explaining real FCM integration steps; `NotificationPayload` builder/factory helper
- AGP migration path documentation — KDoc block in `build.gradle.kts` explaining why AGP is not used and what changes when migrating (tracked for v1.2.0)
- Contract compliance tests (`ContractComplianceTest`) — verifies every service implements the correct Foundation interface, can be instantiated, and primary method returns the expected type
- **DeviceServices (Item 1)**: `BuildDeviceProvider` now stores the device UUID under canonical key `"syzygy.device.uuid"` via `SharedPreferencesStorageProvider`; UUID is generated once on first access and retrieved on all subsequent calls — never regenerated when already stored (requires injecting a persistent StorageProvider; defaults to in-memory if not provided)
- **Persistence (Item 2)**: `SharedPreferencesStorageProvider.get()` and `EncryptedStorageProvider.get()` now throw `StorageTypeMismatchError` (a `SyzygyError` with code `decoding_failed`) when the deserializer throws, instead of silently swallowing the error; error message includes the key name and cause
- **NetworkClient (Item 3)**: `OkHttpNetworkClient` accepts an optional `logger: LoggerProtocol?` parameter (defaults to `null`, zero overhead when absent); logs request method, URL, headers minus `Authorization`, and body size at DEBUG; logs response status, elapsed time, and body size at DEBUG; logs HTTP errors at ERROR and transport errors at WARNING
- **Analytics (Item 4)**: `ConsoleAnalyticsProvider.sessionId` resets to a fresh UUID on every `reset()` call; `sessionId` is injected into every tracked event's properties under `"session_id"` key
- **CrashReporting (Item 5)**: `CrashReporter` interface gains `leaveBreadcrumb(message, metadata?)` and `clearBreadcrumbs()`; `ConsoleCrashReporter` stores the last 20 breadcrumbs in an `ArrayDeque` circular buffer (oldest evicted when full); breadcrumbs are included in `recordError` and `reportCrash` output
- **Dispose/cleanup (Item 7)**: `OkHttpNetworkClient` and `OkHttpWebSocketProvider` now implement `AutoCloseable`; `close()` cancels active jobs, shuts down the OkHttp dispatcher, evicts pooled connections, and closes channels; calls to `connect`/`execute`/`sendText`/`sendBinary` after `close()` throw `IllegalStateException`
- Added `canUseBiometric()` and `authenticateWithBiometric(reason:)` stub methods to `JWTAuthProvider` — returns `false`/`AuthState.Unauthenticated` with doc comments explaining real platform wiring (BiometricPrompt)
- Added `CONTRACT_TESTS.md` — canonical set of behaviour assertions every Services implementation must satisfy, organised by module

### Changed

- BackoffStrategy renamed to BackoffClock; ExponentialBackoffStrategy renamed to ExponentialBackoffClock for cross-platform naming consistency

### Fixed

- README install snippet version corrected to 1.1.0
- README version badge corrected to 1.1.0
- README banner updated to match cross-repo standard
- README modules table class names updated to v1.1.0 implementations
- Retry integration tests now inject TestBackoffClock to verify actual client retry behavior
- Persistence type-mismatch error now correctly names the requested type via reified generic (getTyped method on concrete providers)

## [1.0.0] - 2026-09-12

### Added

- NetworkClient interface and OkHttp-style coroutine HTTP client stub
- StorageProvider interface and SharedPreferences-backed key-value persistence
- AuthProvider interface and JWT token storage and refresh stub
- FileProvider interface and java.io.File-backed file I/O
- PushProvider interface and FCM token registration stub
- DeviceProvider interface and Android Build-based device info implementation
- RemoteConfigProvider interface and in-memory remote config store
- AnalyticsProvider interface and console analytics event logging stub
- CrashReporter interface and console crash logging stub
- WebSocketProvider interface and OkHttp-style WebSocket stub

[1.1.0]: https://github.com/Syzygy-Hub/syzygy-services-android/releases/tag/1.1.0
[1.0.0]: https://github.com/Syzygy-Hub/syzygy-services-android/releases/tag/1.0.0
