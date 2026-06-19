# Export Verification Report

## Verification Checklist & Results

| Export Type | Implemented | Tested | Result |
| :--- | :--- | :--- | :--- |
| **SQLite Database (`grape.sqlite`)** | Yes | Yes | **PASS**. The raw SQLite database is copied from the app database directory into the temporary export folder. |
| **Daily Metrics (`metrics.json`)** | Yes | Yes | **PASS**. Queries `daily_recovery_metrics` and `external_sleep_sessions` tables and dumps them as a structured JSON file. |
| **Baselines (`baselines.json`)** | Yes | Yes | **PASS**. Queries baseline tables (`baseline_daily`, `baseline_30d`, `baseline_90d`, `baseline_lifetime`) and exports them. |
| **Trends (`trends.json`)** | Yes | Yes | **PASS**. Exports daily baseline trends from SQLite into `trends.json`. |
| **Insights (`insights.json`)** | Yes | Yes | **PASS**. Dumps custom AI and heuristics insights from the SQLite `insights` table. |
| **Device Info (`device_info.json`)** | Yes | Yes | **PASS**. Packages diagnostic settings and connected wearable details from `device_info` and `device_settings`. |
| **ZIP Archive Generation** | Yes | Yes | **PASS**. Aggregates all components into a single `grape_export_[timestamp].zip` file inside the cache directory. |
| **Android Sharesheet Integration** | Yes | Yes | **PASS**. Successfully registers a `FileProvider`, grants temporary read permissions, and launches `Intent.ACTION_SEND` to share the ZIP. |

## Implementation Details

- **Utility Location**: [`ExportManager.kt`](file:///home/sugarcube/Desktop/VGPL/Grape/app/mobile/app/src/main/java/com/grape/mobile/utils/ExportManager.kt)
- **Integration Screen**: [`ProfileScreen.kt`](file:///home/sugarcube/Desktop/VGPL/Grape/app/mobile/app/src/main/java/com/grape/mobile/screens/ProfileScreen.kt) (triggering button `Export Data` under *EXPORT DATA* card layout).
- **File Provider Path Configuration**: Registered in XML resource directory as `file_paths.xml` granting read access to `<cache-path name="exports" path="exports/" />` and `<cache-path name="cached_exports" path="/" />`.
