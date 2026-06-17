# Signal Pipeline

This document charts the path raw telemetry frames take from Bluetooth notification packets to computed health metrics.

## Signal Processing Pipeline

```text
       [ WHOOP GATT Notification Event ]
                       │
                       ▼
      [ Android BluetoothGatt Callback ]
                       │ (Passes characteristic payload byte array)
                       ▼
     [ GrapeBleManager / packet deframer ]
                       │ (Verifies 0xaa header start marker and lengths)
                       ▼
            [ UniFFI JNI Boundary ]
                       │ (Invokes insert_packet FFI endpoint in Rust)
                       ▼
       [ Rust core / libgrape_core.so ]
                       ├── Writes raw bytes to `ble_raw_notifications`
                       ├── Parses frame domain & packet_type (protocol.rs)
                       └── Inserts details into `decoded_frames`
                               │
            ┌──────────────────┴──────────────────┐
            ▼ (If Real-time packet)               ▼ (If History packet)
[ Heart Rate Stream ]                 [ Historical Sync Spool ]
    │                                     │
    ├── Realtime HR: updates UI           ├── Saves ranges telemetries
    └── HRV (RR-intervals) calculation    ├── Updates mirror queues
            │                                     │
            ▼                                     ▼
[ SQLite Mirror Sync / metrics rollup calculations (metrics.rs) ]
            │
            ├── Computes Strain, Sleep stage limits, and Recovery scores
            └── Saves variables to `metric_values` and `daily_recovery_metrics`
```

## Buffering & Spooling Strategy

* **Deframing Accumulator**: Incoming packets are fed into a stream deframer (`FrameAccumulator` in `protocol.rs`) to assemble split payloads before decoding.
* **Database Mirror Spools**: To prevent locking the UI thread during high-frequency telemetry floods, writes are batch-inserted into SQLite using background queues.
* **Sensor Signal Pipeline**: HRV and heart rate signals are processed dynamically to compute moving averages and filter premature ventricular contractions or motion artifacts before publishing to the UI.
