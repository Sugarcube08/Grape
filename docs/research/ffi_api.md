# FFI Interface Redesign

This document audits the legacy C FFI bridge in Grape and describes the UniFFI-based redesign implemented for Grape on Android.

## Current FFI Design: `grape_bridge_handle_json()`

In Grape, the Swift-to-Rust communication relies on a single string-based FFI endpoint:

```c
char* grape_bridge_handle_json(const char* request_json);
void grape_bridge_free_string(char* value);
```

### Why it must NOT be migrated to Android

1. **Serialization Overhead**: Every function call requires converting inputs into a JSON string on the host side (Kotlin/Swift), parsing it in Rust, creating a response JSON in Rust, and parsing it back on the host side. This introduces massive CPU/memory overhead for high-frequency packets.
2. **Type Safety Violations**: The FFI signature is untyped (`const char*`). Typo errors inside JSON string keys are caught only at runtime, leading to silent calculation failures or app crashes.
3. **Threading & Memory Management Risks**: Manually tracking allocations, copying pointers, and freeing raw C-string pointers across JNI boundaries is error-prone and frequently results in memory leaks or segmentation faults on Android.

---

## Redesigned FFI: UniFFI Bindings (`ffi/grape.udl`)

Grape replaces the dynamic JSON FFI with strongly-typed bindings generated automatically via **UniFFI**.

### Exported Functions

```uniffi
namespace grape {
    SleepScoreOutput compute_sleep(SleepInput input);
    RecoveryOutput compute_recovery(RecoveryInput input);
    StrainOutput compute_strain(StrainInput input);
    StressOutput compute_stress(StressInput input);
    
    DatabaseWriteReport insert_packet(string database_path, string frame_hex, string device_type);
    HistoricalSyncReport historical_sync(string database_path, string session_id, string range_telemetry_hex);
    
    DatabaseExportReport database_export(string database_path, string output_dir);
    
    DailyMetricsReport get_daily_metrics(string database_path, string date_key);
    WorkoutSessionsReport get_workout_sessions(string database_path, string start_date, string end_date);
    DeviceStateReport get_device_state(string database_path);
};
```

### Advantages of UniFFI for Android (Kotlin)
* **Zero Manual JNI Code**: UniFFI generates the matching JNI glue (`.so` bindings) and Kotlin wrappers (`.kt`) directly from the Interface Definition Language (`.udl`).
* **Strong Compile-Time Type Safety**: Kotlin compiler validates all inputs and outputs at build time.
* **Direct Memory Management**: Object lifecycles and garbage collection are automatically managed by the generated bindings.
