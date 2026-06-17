# Metrics Catalog

This catalog details the biometric variables tracked in Grape, mapping their structures to the underlying SQLite mirror schema.

## Core Biometrics

| Metric | DataType | Unit | DB Table | Algorithm ID | Description |
| ------ | -------- | ---- | -------- | ------------ | ----------- |
| **Heart Rate (HR)** | INTEGER | bpm | `metric_values` | Direct GATT | Real-time and historical heart rate samples. |
| **HRV RMSSD** | REAL | ms | `daily_recovery_metrics` | `grape.hrv.v0` | Autonomic activity metric computed over intervals. |
| **Resting Heart Rate**| REAL | bpm | `daily_recovery_metrics` | `resting_hr_features` | Minimum heart rate baseline computed during deep sleep. |
| **Respiratory Rate** | REAL | rpm | `daily_recovery_metrics` | `respiratory_rate_validation` | Number of breaths per minute parsed from micro-vitals. |
| **Blood Oxygen (SpO₂)**| REAL | % | `daily_recovery_metrics` | `oxygen_saturation_validation` | Oxygen concentration percentage. |
| **Skin Temperature** | REAL | °C | `daily_recovery_metrics` | `temperature_validation` | Deviations from the baseline skin temperature. |
| **Sleep Performance** | REAL | % | `metric_values` | `grape.sleep.v1` | Ratio of actual sleep duration to target sleep needed. |
| **Sleep Needed** | REAL | mins | `metric_values` | `grape.sleep.v1` | Baseline sleep required including debt and workouts. |
| **Sleep Debt** | REAL | mins | `metric_values` | `grape.sleep.v1` | Accumulated sleep deficit from previous nights. |
| **Restorative Sleep** | REAL | mins | `metric_values` | `grape.sleep.v1` | Combined duration in Deep and REM sleep stages. |
| **Sleep Efficiency** | REAL | % | `metric_values` | `grape.sleep.v1` | Ratio of minutes asleep to minutes in bed. |
| **Daily Strain** | REAL | score | `daily_activity_metrics` | `grape.strain.v0` | 0.0 to 21.0 score mapping cardiac load. |
| **Steps** | INTEGER | count| `daily_activity_metrics` | `step_counter` | Daily step count rollups. |
| **Active Energy** | REAL | kcal | `daily_activity_metrics` | `energy_rollup` | Calories expended from movement. |
| **Resting Energy** | REAL | kcal | `daily_activity_metrics` | `energy_rollup` | Basal metabolic energy consumption. |
| **Daily Stress** | REAL | score | `metric_values` | `grape.stress.v0` | Stress metrics mapped across windows. |

## Schema Verification Notes

* **`daily_recovery_metrics`**: Primarily stores daily vitals (HRV, resting HR, respiratory rate, SpO₂, skin temperature) compiled overnight.
* **`daily_activity_metrics`**: Holds workout summary details (steps, active/resting/total calories, average cadence) for a specific date.
* **`metric_values`**: Holds generic outputs from algorithm runs (e.g. sleep debt, restorative sleep fraction, strain indices) referencing `run_id`s in `algorithm_runs`.
