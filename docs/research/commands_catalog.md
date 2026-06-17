# BLE Protocol Commands Catalog

This catalog details all commands parsed by `Rust/core/src/commands.rs` that can be sent by the host app to the wearable device.

## Commands List

| Command ID | Command Code (u8) | Risk Profile | Target Family | Description |
| ---------- | ----------------- | ------------ | ------------- | ----------- |
| **`link_valid`** | 1 | ReadOnly | identity | Read or confirm link-valid protocol state. |
| **`get_max_protocol_version`** | 2 | ReadOnly | identity | Read maximum supported strap protocol version. |
| **`toggle_realtime_hr`** | 3 | UserVisibleStateChange | sensor_stream | Toggle realtime heart-rate packets. |
| **`report_version_info`** | 7 | ReadOnly | identity | Read version information from strap. |
| **`set_clock`** | 10 | UserVisibleStateChange | clock_sync | Write current epoch seconds and subseconds. |
| **`get_clock`** | 11 | ReadOnly | clock_sync | Read strap RTC clock seconds/subseconds. |
| **`toggle_generic_hr_profile`**| 14 | UserVisibleStateChange | sensor_stream | Toggles generic BLE GATT HR profile path. |
| **`toggle_r7_data_collection`** | 16 | UserVisibleStateChange | sensor_stream | Toggles R7 data collection modes. |
| **`run_haptic_pattern_maverick`**| 19 | UserVisibleStateChange | alarm_haptics | Fire a Maverick haptics vibration test. |
| **`abort_historical_transmits`** | 20 | UserVisibleStateChange | historical_sync | Cancel active history packet streams. |
| **`get_hello`** | 145 | ReadOnly | identity | Fetch device identity and version greetings. |
| **`get_battery_level`** | 26 | ReadOnly | battery | Get battery level reading. |
| **`get_data_range`** | 34 | ReadOnly | historical_sync | Read old/new boundary pages of stored logs. |
| **`send_historical_data`** | 22 | UserVisibleStateChange | historical_sync | Start batched historical packet download. |
| **`historical_data_result`** | 23 | UserVisibleStateChange | historical_sync | Acknowledge/result report of history transfer. |
| **`set_read_pointer`** | 33 | UserVisibleStateChange | historical_sync | Move strap read logs pointer to page index. |
| **`get_hello_harvard`** | 35 | ReadOnly | identity | Read legacy Harvard/Gen4 version greeting. |
| **`start_firmware_load`** | 36 | CriticalStateChange | firmware_dfu | Initiate firmware loader. |
| **`load_firmware_data`** | 37 | CriticalStateChange | firmware_dfu | Upload firmware bundle chunk. |
| **`process_firmware_image`** | 38 | CriticalStateChange | firmware_dfu | Instruct device to process firmware image. |
| **`set_alarm_time`** | 66 | UserVisibleStateChange | alarm_haptics | Write wake-up alarm schedule to strap. |
| **`get_alarm_time`** | 67 | ReadOnly | alarm_haptics | Read wake-up alarm schedule from strap. |
| **`run_alarm`** | 68 | UserVisibleStateChange | alarm_haptics | Execute immediate haptics alarm. |
| **`disable_alarm`** | 69 | UserVisibleStateChange | alarm_haptics | Disable scheduled alarm. |
| **`stop_haptics`** | 122 | UserVisibleStateChange | alarm_haptics | Halt ongoing vibration. |
| **`select_wrist`** | 123 | UserVisibleStateChange | wrist_selection | Set active wrist (left or right). |
| **`get_body_location_and_status`**| 84 | ReadOnly | wrist_selection | Get sensor position (wrist/bicep/off-body). |
| **`enter_high_freq_sync`** | 96 | UserVisibleStateChange | historical_sync | Command strap to enter high-speed sync. |
| **`exit_high_freq_sync`** | 97 | UserVisibleStateChange | historical_sync | Exit high-speed sync. |
| **`get_extended_battery_info`** | 98 | ReadOnly | battery | Read detailed fuel-gauge registers. |
| **`toggle_imu_mode`** | 106 | UserVisibleStateChange | sensor_stream | Enable raw IMU acceleration stream. |
| **`enable_optical_data`** | 107 | UserVisibleStateChange | sensor_stream | Enable real-time optical PPG details. |
| **`set_device_config_value`** | 119 | CriticalStateChange | device_config | Write device config value. |
| **`get_device_config_value`** | 121 | ReadOnly | device_config | Read device config value. |

## Risk Management Rules

* **`ReadOnly`**: Can be dispatched by the background worker automatically during standard sync cycles.
* **`UserVisibleStateChange`**: Requires dynamic intent or state lock before dispatch (e.g. user toggling live telemetry, setting an alarm time).
* **`CriticalStateChange`**: Blocks execution entirely unless the app has entered a secure developer/admin mode.
