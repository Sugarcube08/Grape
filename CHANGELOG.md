# Changelog

All notable changes to the Grape project will be documented in this file.

## [v0.3.1-alpha] - 2026-06-19

### Added
- **True Translucent Blurred Bottom Navigation Bar**: Configured [FloatingBottomBar](file:///home/sugarcube/Desktop/VGPL/Grape/app/mobile/app/src/main/java/com/grape/mobile/ui/components/FloatingBottomBar.kt) to use `RenderEffect` for API >= 31 with a custom semi-transparent overlay fallback for API 29–30, matching modern premium design patterns.
- **Data Export Pipeline**: Completed [ExportManager](file:///home/sugarcube/Desktop/VGPL/Grape/app/mobile/app/src/main/java/com/grape/mobile/utils/ExportManager.kt) to copy `grape.sqlite` and generate JSON summaries for daily metrics, baselines, trends, insights, and device settings into a zip archive shared via the Android Sharesheet.

### Fixed
- **DeviceScreen Insets Layout**: Cleaned up the nested layout structure in [DeviceScreen](file:///home/sugarcube/Desktop/VGPL/Grape/app/mobile/app/src/main/java/com/grape/mobile/screens/DeviceScreen.kt) to match `DashboardScreen` and `ProfileScreen`, resolving bottom inset inconsistencies and ensuring a pixel-identical navigation bar position.
- **Back Invocation Warning**: Added `android:enableOnBackInvokedCallback="true"` inside `AndroidManifest.xml` to align with Android 14/15 predictable back gesture behavior and remove system console warnings.
