# Validation and Quality Gates

This document outlines the validation rules, schemas, and checks implemented in the Rust core to preserve data integrity and prevent corrupted inputs from skewing biometric analytics.

## Validation Modules

### 1. Sleep Quality Validation (`sleep_validation.rs`)
* **Stage Sequence Integrity**: Verifies that sleep stages (light, deep, REM, awake) are logically consistent and do not overlap.
* **Release Gates**: Implements rules checking whether a calculated sleep night can be promoted from "experimental" to "trusted" for recovery rollups. Checks include:
  * Minimum total sleep duration (> 120 minutes).
  * Sufficient heart rate variability (HRV) sample density during restorative cycles.
  * Consistency of the wake time boundaries vs subsequent awake signals.

### 2. Database Diagnostics (`store.rs` & `storage_check.rs`)
* **Storage Diagnostics**: Performs self-tests on the SQLite database tables.
* **Version Control**: Migration checks ensure schema updates do not orphan historical entries in `ble_raw_notifications` or `raw_evidence`.

### 3. Local Health Manifest Validation (`local_health_validation.rs`)
* Runs diagnostic suites over the database to confirm consistency between raw packet inputs and final computed scores.
* Outputs reports highlight gaps, telemetry packet drops, and missing calibration datasets.

### 4. Calibration Engine (`calibration.rs`)
* Runs linear calibration models comparing calculated values to user labels.
* Employs train/holdout dataset splits to ensure the models do not overfit.
