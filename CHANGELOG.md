# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[1.0.0]: https://github.com/Syzygy-Hub/syzygy-services-android/releases/tag/1.0.0
