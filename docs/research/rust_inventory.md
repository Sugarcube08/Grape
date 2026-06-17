# Rust Core Modules Inventory

This inventory documents all backend modules parsed from the Grape Rust reference implementation.

| Module | Purpose | Line Count | Migration Status | Dependencies |
| ------ | ------- | ---------- | ---------------- | ------------ |
| `activity_candidates.rs` | Helper or test component. | 706 | KEEP | serde_json, serde |
| `activity_identity.rs` | Helper or test component. | 281 | KEEP | serde_json, serde, sha2 |
| `activity_sessions.rs` | Helper or test component. | 145 | KEEP | serde_json, serde |
| `algorithm_compare.rs` | Helper or test component. | 1294 | KEEP | serde_json, serde |
| `bridge.rs` | FFI request router; processes JSON request packets from host interfaces and calls matching logic. | 8153 | REPLACE (JSON FFI is replaced by UniFFI) | serde_json, serde |
| `calibration.rs` | Calibrates linear models on client vitals. | 905 | KEEP | serde_json, serde |
| `capture_correlation.rs` | Correlates captured frames and parses sensor data gaps. | 683 | KEEP | serde |
| `capture_import.rs` | Helper or test component. | 1412 | KEEP | rusqlite, serde |
| `capture_sanitize.rs` | Sanitizes raw captures to scrub private IDs and identifiers. | 1072 | KEEP | serde, sha2 |
| `commands.rs` | Command definitions, packet validations, and haptic control structures. | 3990 | KEEP | serde |
| `debug_ws.rs` | Helper or test component. | 861 | KEEP | serde_json, serde |
| `debug_ws_server.rs` | Helper or test component. | 361 | REMOVE / INACTIVE (Unused in mobile production) | serde |
| `energy_rollup.rs` | Hourly and daily caloric rollup calculations based on motion and heart rate features. | 1748 | KEEP | serde_json, serde |
| `error.rs` | Standard custom errors mapped across Grape. | 47 | KEEP |  |
| `export.rs` | Helper or test component. | 8228 | KEEP | rusqlite, zip, serde, serde_json |
| `fixtures.rs` | Helper or test component. | 937 | KEEP | serde, sha2 |
| `health_sync.rs` | Helper or test component. | 2035 | KEEP | serde |
| `historical_sync.rs` | Helper or test component. | 2069 | KEEP | serde |
| `lib.rs` | Rust static library root; defines the C FFI boundaries and string allocation/free helpers. | 44 | KEEP |  |
| `local_health_validation.rs` | Helper or test component. | 3280 | KEEP | rusqlite, serde_json, serde |
| `metric_features.rs` | Extracts motion, heart rate, and window features from raw packets for algorithm inputs. | 6438 | KEEP | serde_json, serde |
| `metric_readiness.rs` | Helper or test component. | 603 | KEEP | serde |
| `metrics.rs` | Core wearable metrics algorithms including HRV, sleep stage scoring, strain, stress, and recovery. | 3390 | KEEP | serde_json, serde |
| `openwhoop_reference.rs` | Reference service UUIDs and characteristic mappings from prior art. | 472 | KEEP |  |
| `perf_budget.rs` | Helper or test component. | 794 | REMOVE / INACTIVE (Unused in mobile production) | serde_json, serde |
| `privacy_lint.rs` | Helper or test component. | 580 | KEEP | zip, serde |
| `property_tests.rs` | Helper or test component. | 1218 | KEEP | serde_json, serde |
| `protocol.rs` | WHOOP custom packet deframer and binary parser; handles header reads, CRC validations, and channel streams. | 825 | KEEP | serde |
| `recovery_rollup.rs` | Helper or test component. | 1564 | KEEP | serde_json, serde |
| `reference.rs` | Helper or test component. | 622 | KEEP | serde_json, serde |
| `report.rs` | Helper or test component. | 20 | KEEP | serde |
| `sleep_validation.rs` | Runs verification on sleep stage logs and sleep window consistency. | 6336 | KEEP | serde_json, serde, sha2 |
| `step_counter.rs` | Rolls up step counters from raw packet fields. | 1260 | KEEP | serde_json, serde |
| `step_discovery.rs` | Helper or test component. | 1084 | KEEP | serde_json, serde |
| `step_motion_estimator.rs` | Calculates steps by estimating motion peak signals. | 935 | KEEP | serde_json, serde |
| `storage_check.rs` | Helper or test component. | 926 | KEEP | serde |
| `store.rs` | SQLite persistence backend; runs schemas migrations, capture session saves, and metric reads. | 7585 | KEEP | rusqlite, serde, serde_json, sha2 |
| `timeline.rs` | Helper or test component. | 885 | KEEP | serde_json, serde |
| `tool_args.rs` | Helper or test component. | 32 | KEEP |  |
| `ui_coverage.rs` | Helper or test component. | 1074 | REMOVE / INACTIVE (Unused in mobile production) | serde, sha2 |
| `validation_labels.rs` | Helper or test component. | 44 | KEEP | serde_json |


## Module Details

### activity_candidates.rs

* **Purpose**: Helper or test component.
* **Line Count**: 706
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `ActivityEvidenceProvenance`, `ActivityFeatureWindowInput`, `ActivityCandidateClassifierInput`, `ActivityMotionEvidence`, `ActivityCandidateClassifierOptions`, `ActivityCandidateWindowReport`, `ActivityCandidateNextAction`, `ActivityCommandSyncEvidence`, `ActivityHeartRateEvidence`, `ActivityGravitySample`

---

### activity_identity.rs

* **Purpose**: Helper or test component.
* **Line Count**: 281
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde, sha2
* **Sample Public APIs**: `UtcInstant`, `ActivityIdentityInput`, `canonical_json`, `parse_date`, `canonical_time`, `is_leap_year`, `canonical_raw_identifiers`, `activity_idempotency_key`, `split_time_and_offset`, `days_from_civil`

---

### activity_sessions.rs

* **Purpose**: Helper or test component.
* **Line Count**: 145
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `ActivitySessionCorrectionPlan`, `ActivitySessionCorrectionHistoryEntry`, `ActivitySessionCorrectionKind`, `append_activity_session_correction_history`, `activity_session_correction_plans`, `activity_session_correction_plan`

---

### algorithm_compare.rs

* **Purpose**: Helper or test component.
* **Line Count**: 1294
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `AlgorithmComparisonDelta`, `ComparisonParts`, `AlgorithmComparisonNextAction`, `ExternalReferenceReport`, `AlgorithmComparisonReport`, `push_sleep_external_delta`, `push_delta`, `algorithm_comparison_error_action`, `sleep_v1_benchmark_acceptance_summary`, `comparison_report`

---

### bridge.rs

* **Purpose**: FFI request router; processes JSON request packets from host interfaces and calls matching logic.
* **Line Count**: 8153
* **Migration Status**: REPLACE (JSON FFI is replaced by UniFFI)
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `DebugFinishCommandArgs`, `ApplyCalibrationArgs`, `SleepCorrectionLabelListArgs`, `SleepV1ExplanationStabilityArgs`, `ActivitySessionCorrectionArgs`, `CaptureListSessionsArgs`, `MotionFeaturesArgs`, `EnergyDailyRollupArgs`, `ActivitySessionListArgs`, `EvaluateStoredCalibrationLabelsArgs`

---

### calibration.rs

* **Purpose**: Calibrates linear models on client vitals.
* **Line Count**: 905
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `LeakageChecks`, `ScoreBandBias`, `CalibrationApplicationReport`, `CalibrationApplicationInput`, `CalibrationRecord`, `LinearCalibrationModel`, `CalibrationReport`, `CalibrationMetrics`, `CalibrationNextAction`, `CalibrationRunParams`

---

### capture_correlation.rs

* **Purpose**: Correlates captured frames and parses sensor data gaps.
* **Line Count**: 683
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `CaptureCorrelationSummary`, `CaptureCorrelationNextAction`, `CapturedFrameBatchFixtureFile`, `CaptureCorrelationObservation`, `CaptureCorrelationOptions`, `SummaryAccumulator`, `CaptureCorrelationReport`, `run_capture_correlation`, `body_summary_kind`, `is_synthetic_fixture`

---

### capture_import.rs

* **Purpose**: Helper or test component.
* **Line Count**: 1412
* **Migration Status**: KEEP
* **Imports / Dependencies**: rusqlite, serde
* **Sample Public APIs**: `CaptureImportReport`, `CapturedFrameInput`, `CaptureImportFixtureResult`, `CapturedFrameBatchOptions`, `CapturedFrameBatchFixtureFile`, `CapturedFrameImportResult`, `CaptureSqliteImportReport`, `CapturedFrameBatchOutputOptions`, `CaptureImportNextAction`, `CapturedFrameBatchTimingReport`

---

### capture_sanitize.rs

* **Purpose**: Sanitizes raw captures to scrub private IDs and identifiers.
* **Line Count**: 1072
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde, sha2
* **Sample Public APIs**: `CaptureSanitizeRedactions`, `CaptureSanitizeManifest`, `SanitizedFileManifest`, `CaptureSanitizeTotals`, `CaptureSanitizeOptions`, `CaptureSanitizeReport`, `SanitizedFileReport`, `CaptureSanitizeNextAction`, `CaptureFileFormat`, `KeyTreatment`

---

### commands.rs

* **Purpose**: Command definitions, packet validations, and haptic control structures.
* **Line Count**: 3990
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `CommandEmulatorLogNextAction`, `CommandLocalFrameCandidateFile`, `CommandDefinition`, `CommandValidationReport`, `CommandLocalFrameMatchReport`, `ValidatedCommandEndpoint`, `CommandCapturePlanFamilyAccumulator`, `CommandEmulatorLogEvidenceOptions`, `EmulatorCommandResponseRecord`, `CommandValidationResult`

---

### debug_ws.rs

* **Purpose**: Helper or test component.
* **Line Count**: 861
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `DebugCommandResultStatus`, `DebugSessionStartInput`, `DebugEventInput`, `DebugCommandFinishInput`, `DebugCommandStartInput`, `DebugWsContractReport`, `DebugBridgeConfig`, `DebugSessionSnapshot`, `DebugEventEnvelope`, `DebugCommandEnvelope`

---

### debug_ws_server.rs

* **Purpose**: Helper or test component.
* **Line Count**: 361
* **Migration Status**: REMOVE / INACTIVE (Unused in mobile production)
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `DebugWsServeReport`, `DebugWsServeNextAction`, `DebugWsServerOptions`, `validate_server_options`, `debug_ws_serve_issue_action`, `bind_debug_ws_listener`, `serve_debug_ws_once`, `serve_debug_ws_listener_once`, `error_response`, `validate_handshake_request`

---

### energy_rollup.rs

* **Purpose**: Hourly and daily caloric rollup calculations based on motion and heart rate features.
* **Line Count**: 1748
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `EnergyCaptureValidationOptions`, `EnergyLabelComparison`, `EnergyDailyRollupReport`, `StepCadenceSupport`, `EnergyCaptureValidationReport`, `EnergyHourlyRollupReport`, `EnergyUnavailableDailyStatusReport`, `EnergyRollupNextAction`, `EnergyUnavailableMetricStatus`, `EnergyHourlyRollupOptions`

---

### error.rs

* **Purpose**: Standard custom errors mapped across Grape.
* **Line Count**: 47
* **Migration Status**: KEEP
* **Sample Public APIs**: `GrapeError`, `io`, `message`, `json`

---

### export.rs

* **Purpose**: Helper or test component.
* **Line Count**: 8228
* **Migration Status**: KEEP
* **Imports / Dependencies**: rusqlite, zip, serde, serde_json, sha2
* **Sample Public APIs**: `LocalHealthMetricReferences`, `RawExportReadinessInput`, `ExportMetricComponentRow`, `ExportMetricValueRow`, `ExportFileManifest`, `SensorSampleContext`, `ExportManifest`, `ExportContentValidation`, `ExportTimeWindow`, `NormalizedSensorSampleTime`

---

### fixtures.rs

* **Purpose**: Helper or test component.
* **Line Count**: 937
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde, sha2
* **Sample Public APIs**: `CapturedFrameBatchFixture`, `FixtureNextAction`, `ParserFixtureReport`, `CapturedFrameFixtureFrame`, `FixtureIndexReport`, `IndexedFixture`, `ParserFixtureResult`, `FixtureMetadata`, `build_fixture_index`, `parser_fixture_report_next_actions`

---

### health_sync.rs

* **Purpose**: Helper or test component.
* **Line Count**: 2035
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `ExistingHealthRecord`, `HealthSyncWindow`, `PlatformMapping`, `BlockedHealthRecord`, `ParsedHealthSyncWindow`, `ActivityHealthSyncDryRunReport`, `ActivitySyncMetric`, `ActivitySyncCandidate`, `BlockedHealthDelete`, `ActivitySyncInterval`

---

### historical_sync.rs

* **Purpose**: Helper or test component.
* **Line Count**: 2069
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `HistoricalSyncPhysicalValidationReport`, `HistoricalSyncObservedEvent`, `HistoricalSyncTimestampEvidence`, `HistoricalSyncNextAction`, `HistoricalSyncPhysicalEvidenceTemplate`, `HistoricalSyncPhysicalAcceptanceSummary`, `HistoricalSyncDryRunReport`, `HistoricalSyncPhysicalValidationInput`, `HistoricalSyncResumePlan`, `HistoricalSyncPlanStep`

---

### lib.rs

* **Purpose**: Rust static library root; defines the C FFI boundaries and string allocation/free helpers.
* **Line Count**: 44
* **Migration Status**: KEEP

---

### local_health_validation.rs

* **Purpose**: Helper or test component.
* **Line Count**: 3280
* **Migration Status**: KEEP
* **Imports / Dependencies**: rusqlite, serde_json, serde
* **Sample Public APIs**: `LocalHealthValidationManifestScaffoldOptions`, `ScaffoldEvidenceSummary`, `LocalHealthValidationEvidenceTimeBounds`, `LocalHealthValidationCaptureSessionSummary`, `shell_arg`, `scaffold_operator_checklist`, `string_array`, `capture_session_sql_list`, `scaffold_time_window`, `open_read_only_database`

---

### metric_features.rs

* **Purpose**: Extracts motion, heart rate, and window features from raw packets for algorithm inputs.
* **Line Count**: 6438
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `HrvCaptureValidationReport`, `TemperatureCaptureValidationOptions`, `HeartRatePlan`, `RestingHeartRateFeatureOptions`, `RespiratoryRateCaptureValidationReport`, `RestingHeartRateBaselineFeature`, `RecoveryFeatureScoreOptions`, `MotionFeatureReport`, `RespiratoryRateFeature`, `HrvBaselineFeature`

---

### metric_readiness.rs

* **Purpose**: Helper or test component.
* **Line Count**: 603
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `MetricInputNextAction`, `MetricInputReadiness`, `MetricInputReadinessOptions`, `MetricInputReadinessReport`, `SummaryEvidence`, `ActivitySessionPromotionReadiness`, `MetricFamilyReadiness`, `InputPlan`, `next_actions_for_input`, `dedupe_next_actions`

---

### metrics.rs

* **Purpose**: Core wearable metrics algorithms including HRV, sleep stage scoring, strain, stress, and recovery.
* **Line Count**: 3390
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `SleepScoreOutput`, `SleepNightHistoryInput`, `SleepBaseline`, `StressInput`, `MetricComponent`, `HrvInput`, `SleepInput`, `StrainInput`, `SleepPreviousNightComparison`, `RecoveryInput`

---

### openwhoop_reference.rs

* **Purpose**: Reference service UUIDs and characteristic mappings from prior art.
* **Line Count**: 472
* **Migration Status**: KEEP
* **Sample Public APIs**: `WhoopGenerationReference`, `HistoryFieldReference`, `OpenWhoopHistoryField`, `GrapeSummaryStatus`, `WhoopGeneration`, `WhoopCharacteristicRole`, `service_uuid_lookup_is_generation_aware`, `whoop_generation_reference`, `whoop_generation_references`, `whoop_generation_from_service_uuid`

---

### perf_budget.rs

* **Purpose**: Helper or test component.
* **Line Count**: 794
* **Migration Status**: REMOVE / INACTIVE (Unused in mobile production)
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `PerfBudgetNextAction`, `PerfBudgetReport`, `WorkloadFinish`, `PerfBudgetOptions`, `PerfWorkspace`, `PerfWorkloadReport`, `PerfBudgets`, `export_workload`, `strain_input`, `perf_budget_issue_reason`

---

### privacy_lint.rs

* **Purpose**: Helper or test component.
* **Line Count**: 580
* **Migration Status**: KEEP
* **Imports / Dependencies**: zip, serde
* **Sample Public APIs**: `PrivacyLintReport`, `PrivacyFinding`, `PrivacyLintNextAction`, `PrivacyLintFileReport`, `push_finding`, `looks_like_jwt`, `collect_files`, `file_name`, `compact_snippet`, `lint_privacy_path`

---

### property_tests.rs

* **Purpose**: Helper or test component.
* **Line Count**: 1218
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `DeterministicRng`, `PropertyGroupReport`, `PropertyFailure`, `GroupBuilder`, `PropertySuiteReport`, `PropertySuiteNextAction`, `PropertySuiteOptions`, `check_stress_bounds`, `check_hrv_bounds`, `algorithm_bounds_properties`

---

### protocol.rs

* **Purpose**: WHOOP custom packet deframer and binary parser; handles header reads, CRC validations, and channel streams.
* **Line Count**: 825
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `ParsedFrame`, `DeframeResult`, `FrameAccumulator`, `I16SeriesSummary`, `ParsedPayload`, `DeviceType`, `DataPacketBodySummary`, `crc16_modbus`, `parse_data_packet_body_summary`, `expected_frame_len`

---

### recovery_rollup.rs

* **Purpose**: Helper or test component.
* **Line Count**: 1564
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `RecoveryUnavailableDailyStatusOptions`, `RestingHeartRateCaptureValidationReport`, `RestingHeartRateRollupNextAction`, `RestingHeartRateDailyRollupOptions`, `RecoverySensorMetricValue`, `RecoverySensorDailyRollupReport`, `RecoveryUnavailableMetricStatus`, `RhrLabelComparison`, `RecoveryUnavailableDailyStatusReport`, `CurrentRestingHeartRateMetric`

---

### reference.rs

* **Purpose**: Helper or test component.
* **Line Count**: 622
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `SleepActigraphyReferenceOutput`, `StressHrvHrReferenceOutput`, `StrainEdwardsReferenceOutput`, `HrvReferenceOutput`, `mean`, `strain_reference_run_record`, `require_finite_positive`, `sample_sd`, `hrv_reference_run_record`, `rmssd`

---

### report.rs

* **Purpose**: Helper or test component.
* **Line Count**: 20
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `write_json_report`

---

### sleep_validation.rs

* **Purpose**: Runs verification on sleep stage logs and sleep window consistency.
* **Line Count**: 6336
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde, sha2
* **Sample Public APIs**: `SleepWindowLabelComparison`, `SleepStageLabelValidationReport`, `SleepWindowLabelValidationReport`, `SleepV1ReleaseGateInput`, `SleepV1ExplanationStabilityAcceptanceSummary`, `SleepV1EvidenceSupportingFileReport`, `SleepStageLabelAcceptanceSummary`, `SleepV1ReleaseGateAcceptanceSummary`, `StageLabelProvenance`, `SleepV1ReleaseGateReport`

---

### step_counter.rs

* **Purpose**: Rolls up step counters from raw packet fields.
* **Line Count**: 1260
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `StepCounterDailyRollupReport`, `ActivityUnavailableDailyStatusReport`, `StepSegmentSummary`, `StepCounterIngestOptions`, `StepCounterIngestReport`, `ActivityUnavailableMetricStatus`, `StepCounterPacketField`, `StepCounterDailyRollupOptions`, `StepCounterHourlyRollupReport`, `StepCounterNextAction`

---

### step_discovery.rs

* **Purpose**: Helper or test component.
* **Line Count**: 1084
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `StepPacketDiscoveryCandidate`, `FrameContext`, `FieldMatch`, `StepCaptureValidationOptions`, `StepCounterDeltaCandidate`, `StepPacketDiscoveryOptions`, `StepPacketDiscoveryNextAction`, `StepPacketDiscoveryReport`, `StepCaptureValidationReport`, `selected_counter_delta`

---

### step_motion_estimator.rs

* **Purpose**: Calculates steps by estimating motion peak signals.
* **Line Count**: 935
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `RawMotionStepEstimateNextAction`, `RawMotionStepEstimateReport`, `MotionPlan`, `RawMotionStepFrameEstimate`, `RawMotionStepEstimateOptions`, `persist_validated_raw_motion_step_metric`, `parse_warnings`, `issue_action`, `selected_motion_axes`, `label_match`

---

### storage_check.rs

* **Purpose**: Helper or test component.
* **Line Count**: 926
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde
* **Sample Public APIs**: `StorageCheckNextAction`, `StorageCheckReport`, `StorageSelfTestReport`, `StorageCheckOptions`, `StorageTableCheck`, `storage_self_test_ready`, `check_table`, `check_storage_database`, `storage_check_report_next_actions`, `required_columns`

---

### store.rs

* **Purpose**: SQLite persistence backend; runs schemas migrations, capture session saves, and metric reads.
* **Line Count**: 7585
* **Migration Status**: KEEP
* **Imports / Dependencies**: rusqlite, serde, serde_json, sha2
* **Sample Public APIs**: `ExternalSleepStageRow`, `AlgorithmDefinitionRecord`, `ActivityIntervalRow`, `MetricProvenanceInput`, `MetricProvenanceRow`, `DailyActivityMetricInput`, `CommandValidationRecord`, `OvernightMirrorCounts`, `StepCounterSampleRow`, `CalibrationRunRecord`

---

### timeline.rs

* **Purpose**: Helper or test component.
* **Line Count**: 885
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json, serde
* **Sample Public APIs**: `ObservabilityTimelineRow`, `PacketTimelineRow`, `ObservabilityStage`, `parse_warnings`, `observability_category`, `capture_session_ids_from_debug_rows`, `observability_activity_session_id`, `packet_timeline_from_decoded_frames`, `observability_export_job_id`, `observability_title`

---

### tool_args.rs

* **Purpose**: Helper or test component.
* **Line Count**: 32
* **Migration Status**: KEEP
* **Sample Public APIs**: `flag`, `value`, `path_value`, `args`, `default_path`

---

### ui_coverage.rs

* **Purpose**: Helper or test component.
* **Line Count**: 1074
* **Migration Status**: REMOVE / INACTIVE (Unused in mobile production)
* **Imports / Dependencies**: serde, sha2
* **Sample Public APIs**: `SourceClassCoverageRule`, `UiCoverageRuleMatch`, `UiCoverageNextAction`, `UiCoverageBucketReport`, `UiCoverageAuditReport`, `NavigationCoverageRule`, `UiCoverageRules`, `MissingUiSurface`, `UiCoverageAuditInput`, `LayoutCoverageRule`

---

### validation_labels.rs

* **Purpose**: Helper or test component.
* **Line Count**: 44
* **Migration Status**: KEEP
* **Imports / Dependencies**: serde_json
* **Sample Public APIs**: `official_label_policy_issue_action`, `official_label_policy_issues`

---

