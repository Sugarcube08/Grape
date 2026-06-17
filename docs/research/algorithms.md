# Wearable Analytics Algorithms

This document outlines the core algorithms, features, and formulas compiled inside the shared Rust library (`metrics.rs`, `calibration.rs`, `energy_rollup.rs`, `step_motion_estimator.rs`).

## Mapped Algorithms

### 1. Heart Rate Variability (HRV v0)
* **ID**: `grape.hrv.v0` (Version `0.1.0`)
* **Calculations**:
  * **RMSSD**: Root mean square of successive RR-interval differences. Measures parasympathetic autonomic nervous activity.
  * **SDNN**: Standard deviation of all NN/RR-intervals. Indicates global autonomic balance.
  * **pNN50**: Fraction of successive interval differences exceeding 50ms.
* **Inputs**: Sequence of raw RR-intervals in milliseconds (`Vec<f64>`).
* **Outputs**: RMSSD, SDNN, pNN50, mean NN.

### 2. Sleep Scoring (Sleep v0 & Sleep v1)
* **Sleep v0 (Basic)**: Calculates sleep quality score based on total minutes asleep vs target sleep needed.
* **Sleep v1 (Stage-Aware)**:
  * Analyzes sleep stage proportions (REM, deep, light, awake).
  * Computes restorative sleep fraction (REM + deep sleep minutes vs total sleep time).
  * Evaluates disturbance counts and sleep latency parameters to yield a comprehensive 0-100 performance score.
* **Relevant Files**: `metrics.rs`, `sleep_validation.rs`.

### 3. Exertion Strain (Strain v0)
* **ID**: `grape.strain.v0`
* **Formula**:
  * Exertion is derived by measuring time spent across distinct heart rate training zones (aerobic, anaerobic, recovery).
  * Outputs a logarithmic score ranging from **0.0 to 21.0** representing daily cardiac load.

### 4. Recovery Score (Recovery v0)
* **ID**: `grape.recovery.v0`
* **Formula**:
  * Integrates resting heart rate dip, nighttime HRV RMSSD, skin temperature deviation, and sleep score.
  * Outputs a recovery status score (0-100%) classified as:
    * Red (0-33%): High fatigue, recovery needed.
    * Yellow (34-66%): Normal load capability.
    * Green (67-100%): Optimal readiness.

### 5. Instantaneous Stress Level (Stress v0)
* **ID**: `grape.stress.v0`
* **Mechanism**:
  * Evaluates short-term RMSSD variations and heart rate trends over moving windows to estimate current stress.
  * Separates activity stress (exertion) from quiet-state autonomic stress.

### 6. Caloric Exertion (Energy Rollups)
* **Strategy**:
  * Employs Harris-Benedict formulas using age, weight, biological sex, and resting heart rate to establish basal metabolic rate (BMR).
  * Adds active calories estimated from heart rate exertion metrics and step counts.
* **Relevant Files**: `energy_rollup.rs`.

### 7. Step Motion Estimation
* **Strategy**:
  * Establishes steps from raw accelerometer axes data (`axes` in K10 or K21 frames).
  * Implements a peak-threshold counting algorithm to filter noise.
* **Relevant Files**: `step_motion_estimator.rs`.
