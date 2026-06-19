# Changelog

All notable changes to the Grape project will be documented in this file.

## [v0.3.0-alpha] - 2026-06-19

### Added
- **Truthful Alpha Engine**: Integrated real-time Stress V1 score calculation in Rust Core using RMSSD HRV, skin temperature delta, sleep debt, and strain ratios.
- **SQLite Baselines & Metrics**: Added SQLite schemas and tables for daily, 30-day, 90-day, and lifetime baselines.
- **Hardware Metadata Discovery**: Added direct BLE Device Information Service (DIS) queries to read device details (Manufacturer, Serial, Firmware, Battery level) and store them in `device_info`.
- **Dynamic Telemetry & Provenance**: Implemented end-to-end provenance propagation (`Metric<T>`, `DataOrigin`, `TrendSummary`, `Insight`) from GATT notifications through SQLite and Rust FFI to Compose layouts.
- **Trend Cards UI**: Replaced placeholder weekly heatmaps with dynamic trend cards (Slope, Volatility, Consistency, Direction) and timeline segments bound to actual biometrics.

### Fixed
- **App Startup Path (P0 Blocker)**: Resolved `ClassNotFoundException` by correcting the launcher activity's package reference to `.app.MainActivity` in the manifest.
- **Koin Lifecycle Ordering**: Bound `GrapeApplication` in the manifest to guarantee that Koin dependency injection contexts start before any Android activity or background service.
- **Foreground Service Creation**: Restructured `GrapeBleService`'s `onCreate` method to immediately spin up a lightweight, dependency-free notification and call `startForeground` to eliminate OS startup crashes.
- **Font Subsystem Stability**: Cleaned up debug TTF font resource conflicts and standardized styling around `FontFamily.SansSerif`.
- **JNI Binding and UniFFI Cleanup**: Purged leftover UniFFI configurations to align with the streamlined direct JNI binding loader, reducing app size and avoiding native library loading crashes.
- **Developer Warnings Cleanup**: Clean compiled the entire Android Gradle and Rust Cargo codebase, resolving all deprecations (including `BluetoothGatt` write methods and `BluetoothAdapter` service lookups) and eliminating all dead-code compiler logs.

## [v0.2.0-alpha] - 2026-05-10

### Added
- Companion Device Manager integration designed.
- Historical Sync pipeline skeleton.
- SQLite support.
- Initial Rust FFI bridge layout.
