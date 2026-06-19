# Changelog

All notable changes to the Grape project will be documented in this file.

## [v0.3.2-beta] - 2026-06-20

### Added
- **Sync Stage Diagnostics**: Added a dedicated `SYNC DIAGNOSTICS` panel displaying real-time metrics, connection stages, and exception logs in the UI, enabling robust hardwareless validation.

### Fixed
- **Haze Frosted Blur Crash**: Resolved `IllegalArgumentException: backgroundColor not specified` crash during Compose drawing by providing a custom `HazeStyle` containing an explicit background color.
- **Main Thread Startup Bottlenecks**: Moved heavy initialization workloads (SQLite schemas, BLE manager settings, update metadata checks) to background dispatchers, eliminating UI thread freezes.
- **Foreground Service Compatibility**: Audited and secured Android 14/15 foreground service initialization. Added guards to prevent redundant starts and ensured startForeground is triggered immediately.
