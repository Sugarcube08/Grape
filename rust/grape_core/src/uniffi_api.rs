use crate::capture_import::{
    import_captured_frame_batch_with_output_options, CapturedFrameBatchOptions,
    CapturedFrameBatchOutputOptions, CapturedFrameInput,
};
use crate::protocol::DeviceType;
use crate::store::GrapeStore;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::path::Path;
use std::sync::Mutex;
use std::sync::OnceLock;

#[derive(Serialize, Deserialize, Debug, Clone)]
struct DeviceState {
    connection_state: String,
    heart_rate: i32,
    battery: i32,
    packets_received: i32,
    frames_parsed: i32,
}

static DEVICE_STATE: OnceLock<Mutex<DeviceState>> = OnceLock::new();

fn get_device_state_mutex() -> &'static Mutex<DeviceState> {
    DEVICE_STATE.get_or_init(|| {
        Mutex::new(DeviceState {
            connection_state: "DISCONNECTED".to_string(),
            heart_rate: 0,
            battery: 0,
            packets_received: 0,
            frames_parsed: 0,
        })
    })
}

pub fn compute_sleep(database_path: String) -> String {
    if let Some(report) = compute_sleep_v1(database_path) {
        serde_json::to_string(&report).unwrap_or_else(|_| "{}".to_string())
    } else {
        "{}".to_string()
    }
}

pub fn compute_recovery(database_path: String) -> String {
    if let Some(report) = compute_recovery_v0(database_path) {
        serde_json::to_string(&report).unwrap_or_else(|_| "{}".to_string())
    } else {
        "{}".to_string()
    }
}

pub fn compute_strain(database_path: String) -> String {
    match compute_strain_v1(database_path) {
        Some(report) => serde_json::to_string(&report).unwrap_or_else(|_| "{}".to_string()),
        None => "{}".to_string(),
    }
}

pub fn compute_stress(database_path: String) -> String {
    let store = match GrapeStore::open(Path::new(&database_path)) {
        Ok(s) => s,
        Err(_) => return "{}".to_string(),
    };
    
    let rec_report = compute_recovery_v0(database_path.clone());
    let (recovery_score, hrv, temp_delta) = match rec_report {
        Some(r) => (r.recovery_score, r.hrv, r.temperature_delta),
        None => (70.0, 65.0, 0.0),
    };

    let sleep_report = compute_sleep_v1(database_path.clone());
    let (_, sleep_debt) = match sleep_report {
        Some(s) => (s.efficiency, s.debt),
        None => (85.0, 0.0),
    };

    let strain_report = compute_strain_v1(database_path.clone());
    let strain_score = match strain_report {
        Some(st) => st.strain_score,
        None => 0.0,
    };

    let (mu_30, sigma_30): (f64, f64) = store.conn.query_row(
        "SELECT mean, std_dev FROM baseline_30d WHERE metric_type = 'HRV'",
        [],
        |row| Ok((row.get::<_, f64>(0).unwrap_or(65.0), row.get::<_, f64>(1).unwrap_or(10.0)))
    ).unwrap_or((65.0, 10.0));
    
    let sigma_30 = if sigma_30 <= 0.0 { 10.0 } else { sigma_30 };

    let hrv_score = (50.0 - 50.0 * (hrv - mu_30) / sigma_30).clamp(0.0, 100.0);
    let temp_score = (50.0 * temp_delta).clamp(0.0, 100.0);
    let sleep_debt_penalty = if sleep_debt > 60.0 {
        (100.0 * sleep_debt / 240.0).clamp(0.0, 100.0)
    } else {
        0.0
    };
    let readiness_load = (100.0 - recovery_score) * 0.5 + (strain_score / 21.0 * 100.0) * 0.5;

    let w1 = 0.50;
    let w2 = 0.15;
    let w3 = 0.20;
    let w4 = 0.15;
    let stress_score = w1 * hrv_score + w2 * temp_score + w3 * sleep_debt_penalty + w4 * readiness_load;
    
    let state = if stress_score < 35.0 {
        "LOW"
    } else if stress_score < 70.0 {
        "MEDIUM"
    } else {
        "HIGH"
    };

    let confidence = 0.95;
    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64;

    let _ = store.conn.execute(
        "INSERT INTO daily_stress_metrics (stress_score, state, hrv_contribution, temp_contribution, timestamp) VALUES (?, ?, ?, ?, ?)",
        (stress_score, state, hrv_score, temp_score, now_ms)
    );

    let report_json = serde_json::json!({
        "stress_score": stress_score,
        "state": state,
        "confidence": confidence,
        "timestamp": now_ms,
        "hrv_contribution": hrv_score,
        "temp_contribution": temp_score
    });
    
    report_json.to_string()
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct SleepReport {
    pub score: f64,
    pub need: f64,
    pub debt: f64,
    pub efficiency: f64,
    pub latency: f64,
    pub disturbances: u32,
    pub rem_minutes: f64,
    pub deep_minutes: f64,
    pub light_minutes: f64,
    pub awake_minutes: f64,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct RecoveryReport {
    pub recovery_score: f64,
    pub recovery_state: String,
    pub hrv: f64,
    pub resting_hr: f64,
    pub temperature_delta: f64,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct StrainReport {
    pub strain_score: f64,
    pub cardio_load: f64,
    pub muscular_load: f64,
    pub acute_load: f64,
    pub intensity_class: String,
    pub duration_minutes: f64,
    pub active_kcal: f64,
    pub steps: i64,
    pub average_hr: f64,
    pub max_hr: f64,
    pub resting_hr: f64,
    pub sleep_score: f64,
    pub recovery_score: f64,
    pub baselines: BaselineReport,
    pub trends: TrendReport,
    pub quality_flags: Vec<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct BaselineReport {
    pub rolling_30_day: BaselineWindowReport,
    pub rolling_90_day: BaselineWindowReport,
    pub lifetime: BaselineWindowReport,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct BaselineWindowReport {
    pub day_count: u32,
    pub baseline_hrv: f64,
    pub baseline_rhr: f64,
    pub baseline_temp: f64,
    pub baseline_sleep: f64,
    pub baseline_strain: f64,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct TrendReport {
    pub recovery_trend: TrendMetricReport,
    pub hrv_trend: TrendMetricReport,
    pub sleep_trend: TrendMetricReport,
    pub training_trend: TrendMetricReport,
    pub stress_trend: TrendMetricReport,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct TrendMetricReport {
    pub current: f64,
    pub baseline: f64,
    pub delta: f64,
    pub direction: String,
}

#[derive(Debug, Clone)]
struct ActivityPhysiologyRow {
    date_key: String,
    start_time_unix_ms: i64,
    end_time_unix_ms: i64,
    steps: Option<i64>,
    active_kcal: Option<f64>,
    average_cadence_spm: Option<f64>,
    confidence: f64,
    inputs_json: String,
}

#[derive(Debug, Clone, Default)]
struct DerivedStrainDay {
    strain_score: f64,
    cardio_load: f64,
    muscular_load: f64,
    acute_load: f64,
    duration_minutes: f64,
    active_kcal: f64,
    steps: i64,
    average_hr: f64,
    max_hr: f64,
    resting_hr: f64,
    quality_flags: Vec<String>,
}

pub fn compute_sleep_v1(database_path: String) -> Option<SleepReport> {
    let store = GrapeStore::open(Path::new(&database_path)).ok()?;

    // Find the latest external sleep session
    let mut stmt = store.conn.prepare(
        "SELECT start_time_unix_ms, end_time_unix_ms FROM external_sleep_sessions ORDER BY start_time_unix_ms DESC LIMIT 1"
    ).ok()?;

    let (start_unix_ms, end_unix_ms): (i64, i64) = stmt
        .query_row([], |row| Ok((row.get(0)?, row.get(1)?)))
        .ok()?;

    let start_str = format!("unix_ms:{}", start_unix_ms);
    let end_str = format!("unix_ms:{}", end_unix_ms);

    let report = crate::metric_features::run_sleep_feature_score_report_for_store(
        &store,
        &database_path,
        &start_str,
        &end_str,
        crate::metric_features::SleepFeatureScoreOptions {
            min_owned_captures_per_summary: 0,
            require_trusted_evidence: false,
            sleep_need_minutes: 480.0,
            low_motion_threshold_0_to_1: 0.05,
            disturbance_motion_threshold_0_to_1: 0.20,
            target_midpoint_minutes_since_midnight: 180.0,
        },
    )
    .ok()?;

    let sleep_input = report.sleep_input.as_ref()?;

    let sleep_v1_input =
        crate::bridge::sleep_v1_input_from_feature_score(&store, sleep_input, &report, false)
            .ok()?;

    let sleep_v1_result = crate::metrics::grape_sleep_v1(&sleep_v1_input);
    let out = sleep_v1_result.output?;

    Some(SleepReport {
        score: out.score_0_to_100,
        need: out.sleep_need_minutes,
        debt: out.sleep_debt_minutes,
        efficiency: out.sleep_efficiency_fraction * 100.0,
        latency: out.sleep_latency_minutes,
        disturbances: out.wake_episode_count,
        rem_minutes: out.rem_sleep_minutes,
        deep_minutes: out.deep_sleep_minutes,
        light_minutes: out.core_sleep_minutes,
        awake_minutes: out.awake_minutes,
    })
}

pub fn compute_strain_v1(database_path: String) -> Option<StrainReport> {
    let store = GrapeStore::open(Path::new(&database_path)).ok()?;
    let latest = latest_activity_physiology_row(&store)?;
    let recovery = compute_recovery_v0(database_path.clone());
    let sleep = compute_sleep_v1(database_path.clone());
    let recovery_score = recovery.as_ref().map(|r| r.recovery_score).unwrap_or(70.0);
    let sleep_score = sleep.as_ref().map(|s| s.score).unwrap_or(75.0);
    let recovery_resting_hr = recovery.as_ref().map(|r| r.resting_hr);
    let current = derive_strain_day(&latest, recovery_resting_hr, recovery_score, sleep_score)?;
    let history_rows = activity_physiology_rows(&store).unwrap_or_default();
    let mut strain_history = history_rows
        .iter()
        .filter_map(|row| derive_strain_day(row, recovery_resting_hr, recovery_score, sleep_score))
        .collect::<Vec<_>>();
    if strain_history.is_empty() {
        strain_history.push(current.clone());
    }

    let baselines = compute_baseline_report(&store, &strain_history);
    let trends = compute_trend_report(&store, &strain_history, sleep_score, recovery_score);

    Some(StrainReport {
        strain_score: round_1(current.strain_score),
        cardio_load: round_1(current.cardio_load),
        muscular_load: round_1(current.muscular_load),
        acute_load: round_1(current.acute_load),
        intensity_class: strain_intensity_class(current.strain_score).to_string(),
        duration_minutes: round_1(current.duration_minutes),
        active_kcal: round_1(current.active_kcal),
        steps: current.steps,
        average_hr: round_1(current.average_hr),
        max_hr: round_1(current.max_hr),
        resting_hr: round_1(current.resting_hr),
        sleep_score: round_1(sleep_score),
        recovery_score: round_1(recovery_score),
        baselines,
        trends,
        quality_flags: current.quality_flags,
    })
}

pub fn sleep_summary(database_path: String) -> String {
    if let Some(report) = compute_sleep_v1(database_path) {
        format!(
            "Sleep Score: {:.0}%, Duration: {:.1} hrs (REM: {:.0}m, Deep: {:.0}m), Efficiency: {:.0}%",
            report.score,
            (report.rem_minutes + report.deep_minutes + report.light_minutes) / 60.0,
            report.rem_minutes,
            report.deep_minutes,
            report.efficiency
        )
    } else {
        "No sleep data available.".to_string()
    }
}

pub fn sleep_stage_distribution(database_path: String) -> String {
    if let Some(report) = compute_sleep_v1(database_path) {
        format!(
            "{{\"rem\": {}, \"deep\": {}, \"light\": {}, \"awake\": {}}}",
            report.rem_minutes, report.deep_minutes, report.light_minutes, report.awake_minutes
        )
    } else {
        "{}".to_string()
    }
}

pub fn sleep_efficiency(database_path: String) -> f64 {
    compute_sleep_v1(database_path)
        .map(|r| r.efficiency / 100.0)
        .unwrap_or(0.0)
}

pub fn sleep_debt(database_path: String) -> f64 {
    compute_sleep_v1(database_path)
        .map(|r| r.debt)
        .unwrap_or(0.0)
}

pub fn compute_recovery_v0(database_path: String) -> Option<RecoveryReport> {
    let store = GrapeStore::open(Path::new(&database_path)).ok()?;

    // Fetch baselines
    let hrv_baseline_rmssd_ms = store
        .conn
        .query_row(
            "SELECT AVG(hrv_rmssd_ms) FROM daily_recovery_metrics WHERE hrv_rmssd_ms IS NOT NULL",
            [],
            |row| row.get::<_, f64>(0),
        )
        .unwrap_or(65.0);

    let resting_hr_baseline_bpm = store.conn.query_row(
        "SELECT AVG(resting_hr_bpm) FROM daily_recovery_metrics WHERE resting_hr_bpm IS NOT NULL",
        [],
        |row| row.get::<_, f64>(0)
    ).unwrap_or(60.0);

    let respiratory_rate_baseline_rpm = store.conn.query_row(
        "SELECT AVG(respiratory_rate_rpm) FROM daily_recovery_metrics WHERE respiratory_rate_rpm IS NOT NULL",
        [],
        |row| row.get::<_, f64>(0)
    ).unwrap_or(16.0);

    // Fetch latest recovery row
    let mut stmt = store.conn.prepare(
        "SELECT start_time_unix_ms, end_time_unix_ms, resting_hr_bpm, hrv_rmssd_ms, respiratory_rate_rpm, skin_temperature_delta_c FROM daily_recovery_metrics ORDER BY start_time_unix_ms DESC LIMIT 1"
    ).ok()?;

    let (
        start_time_unix_ms,
        end_time_unix_ms,
        resting_hr_bpm,
        hrv_rmssd_ms,
        respiratory_rate_rpm,
        skin_temp_delta_c,
    ) = match stmt.query_row([], |row| {
        Ok((
            row.get::<_, i64>(0)?,
            row.get::<_, i64>(1)?,
            row.get::<_, Option<f64>>(2)?,
            row.get::<_, Option<f64>>(3)?,
            row.get::<_, Option<f64>>(4)?,
            row.get::<_, Option<f64>>(5)?,
        ))
    }) {
        Ok(r) => {
            let resting_hr = r.2.unwrap_or(resting_hr_baseline_bpm);
            let hrv = r.3.unwrap_or(hrv_baseline_rmssd_ms);
            let resp = r.4.unwrap_or(respiratory_rate_baseline_rpm);
            let temp = r.5.unwrap_or(0.0);
            (r.0, r.1, resting_hr, hrv, resp, temp)
        }
        Err(_) => return None,
    };

    let sleep_score_0_to_100 = compute_sleep_v1(database_path)
        .map(|r| r.score)
        .unwrap_or(75.0);

    let input = crate::metrics::RecoveryInput {
        start_time: format!("unix_ms:{}", start_time_unix_ms),
        end_time: format!("unix_ms:{}", end_time_unix_ms),
        hrv_rmssd_ms,
        hrv_baseline_rmssd_ms,
        resting_hr_bpm,
        resting_hr_baseline_bpm,
        respiratory_rate_rpm,
        respiratory_rate_baseline_rpm,
        skin_temp_delta_c,
        sleep_score_0_to_100,
        prior_strain_0_to_21: 0.0,
        input_ids: vec![],
    };

    let run_res = crate::metrics::grape_recovery_v0(&input);
    let out = run_res.output?;
    let score = out.score_0_to_100;

    let state = if score >= 67.0 {
        "GREEN".to_string()
    } else if score >= 34.0 {
        "YELLOW".to_string()
    } else {
        "RED".to_string()
    };

    Some(RecoveryReport {
        recovery_score: score,
        recovery_state: state,
        hrv: hrv_rmssd_ms,
        resting_hr: resting_hr_bpm,
        temperature_delta: skin_temp_delta_c,
    })
}

fn latest_activity_physiology_row(store: &GrapeStore) -> Option<ActivityPhysiologyRow> {
    store
        .conn
        .query_row(
            r#"
        SELECT date_key, start_time_unix_ms, end_time_unix_ms, steps, active_kcal,
               average_cadence_spm, confidence, inputs_json
        FROM daily_activity_metrics
        WHERE source_kind != 'unavailable'
          AND (active_kcal IS NOT NULL OR steps IS NOT NULL OR average_cadence_spm IS NOT NULL)
        ORDER BY start_time_unix_ms DESC, updated_at DESC
        LIMIT 1
        "#,
            [],
            |row| {
                Ok(ActivityPhysiologyRow {
                    date_key: row.get(0)?,
                    start_time_unix_ms: row.get(1)?,
                    end_time_unix_ms: row.get(2)?,
                    steps: row.get(3)?,
                    active_kcal: row.get(4)?,
                    average_cadence_spm: row.get(5)?,
                    confidence: row.get(6)?,
                    inputs_json: row.get(7)?,
                })
            },
        )
        .ok()
}

fn activity_physiology_rows(store: &GrapeStore) -> Option<Vec<ActivityPhysiologyRow>> {
    let mut stmt = store
        .conn
        .prepare(
            r#"
        SELECT date_key, start_time_unix_ms, end_time_unix_ms, steps, active_kcal,
               average_cadence_spm, confidence, inputs_json
        FROM daily_activity_metrics
        WHERE source_kind != 'unavailable'
          AND (active_kcal IS NOT NULL OR steps IS NOT NULL OR average_cadence_spm IS NOT NULL)
        ORDER BY start_time_unix_ms ASC
        "#,
        )
        .ok()?;
    let rows = stmt
        .query_map([], |row| {
            Ok(ActivityPhysiologyRow {
                date_key: row.get(0)?,
                start_time_unix_ms: row.get(1)?,
                end_time_unix_ms: row.get(2)?,
                steps: row.get(3)?,
                active_kcal: row.get(4)?,
                average_cadence_spm: row.get(5)?,
                confidence: row.get(6)?,
                inputs_json: row.get(7)?,
            })
        })
        .ok()?;
    rows.collect::<Result<Vec<_>, _>>().ok()
}

fn derive_strain_day(
    row: &ActivityPhysiologyRow,
    recovery_resting_hr: Option<f64>,
    recovery_score: f64,
    sleep_score: f64,
) -> Option<DerivedStrainDay> {
    let inputs: Value = serde_json::from_str(&row.inputs_json).unwrap_or(Value::Null);
    let duration_minutes = json_f64(&inputs, "covered_minutes")
        .or_else(|| json_f64(&inputs, "requested_minutes"))
        .unwrap_or_else(|| {
            ((row.end_time_unix_ms - row.start_time_unix_ms) as f64 / 60_000.0).max(1.0)
        });
    let active_kcal = row.active_kcal.unwrap_or(0.0).max(0.0);
    let steps = row.steps.unwrap_or(0).max(0);
    let resting_hr = json_f64(&inputs, "resting_hr_bpm")
        .or(recovery_resting_hr)
        .unwrap_or(60.0)
        .max(35.0);
    let max_hr = json_f64(&inputs, "max_hr_bpm")
        .unwrap_or(190.0)
        .max(resting_hr + 20.0);
    let average_hr = json_f64(&inputs, "average_hr_bpm").unwrap_or_else(|| {
        let kcal_intensity = (active_kcal / duration_minutes.max(1.0)).clamp(0.0, 12.0) / 12.0;
        resting_hr + (max_hr - resting_hr) * (0.15 + kcal_intensity * 0.55)
    });
    let hr_zone_minutes = json_f64_array(&inputs, "hr_zone_minutes")
        .filter(|zones| zones.len() == 5 && zones.iter().sum::<f64>() > 0.0)
        .unwrap_or_else(|| {
            estimated_hr_zone_minutes(duration_minutes, average_hr, resting_hr, max_hr)
        });

    let input = crate::metrics::StrainInput {
        start_time: format!("unix_ms:{}", row.start_time_unix_ms),
        end_time: format!("unix_ms:{}", row.end_time_unix_ms),
        duration_minutes,
        resting_hr_bpm: resting_hr,
        average_hr_bpm: average_hr,
        max_hr_bpm: max_hr,
        hr_zone_minutes,
        input_ids: vec![format!("daily_activity:{}", row.date_key)],
    };
    let run = crate::metrics::grape_strain_v0(&input);
    let output = run.output?;
    let recovery_modifier = (1.0 + (70.0 - recovery_score).max(-20.0) / 200.0).clamp(0.90, 1.18);
    let sleep_modifier = (1.0 + (75.0 - sleep_score).max(-25.0) / 250.0).clamp(0.92, 1.15);
    let acute_load =
        (output.zone_load / 30.0 * recovery_modifier * sleep_modifier).clamp(0.0, 21.0);
    let cadence_load =
        row.average_cadence_spm.unwrap_or(0.0).max(0.0) / 180.0 * duration_minutes / 25.0;
    let step_load = steps as f64 / 12_000.0 * 4.0;
    let muscular_load = (cadence_load + step_load + active_kcal / 250.0).clamp(0.0, 21.0);
    let cardio_load = (output.zone_load / 20.0).clamp(0.0, 21.0);
    let strain_score =
        (output.score_0_to_21 * 0.72 + acute_load * 0.18 + muscular_load * 0.10).clamp(0.0, 21.0);
    let mut quality_flags = run.quality_flags;
    if row.confidence < 0.50 {
        quality_flags.push("low_activity_metric_confidence".to_string());
    }
    if json_f64(&inputs, "average_hr_bpm").is_none() {
        quality_flags.push("average_hr_estimated_from_energy".to_string());
    }

    Some(DerivedStrainDay {
        strain_score,
        cardio_load,
        muscular_load,
        acute_load,
        duration_minutes,
        active_kcal,
        steps,
        average_hr,
        max_hr,
        resting_hr,
        quality_flags,
    })
}

fn compute_baseline_report(
    store: &GrapeStore,
    strain_history: &[DerivedStrainDay],
) -> BaselineReport {
    let recovery = recovery_history(store).unwrap_or_default();
    BaselineReport {
        rolling_30_day: baseline_window_report(&recovery, strain_history, 30),
        rolling_90_day: baseline_window_report(&recovery, strain_history, 90),
        lifetime: baseline_window_report(&recovery, strain_history, usize::MAX),
    }
}

fn compute_trend_report(
    store: &GrapeStore,
    strain_history: &[DerivedStrainDay],
    sleep_score: f64,
    recovery_score: f64,
) -> TrendReport {
    let recovery = recovery_history(store).unwrap_or_default();
    let recent_recovery = recovery
        .iter()
        .rev()
        .take(7)
        .filter_map(|r| r.recovery_score)
        .collect::<Vec<_>>();
    let baseline_recovery = recovery
        .iter()
        .rev()
        .take(30)
        .filter_map(|r| r.recovery_score)
        .collect::<Vec<_>>();
    let recent_hrv = recovery
        .iter()
        .rev()
        .take(7)
        .filter_map(|r| r.hrv)
        .collect::<Vec<_>>();
    let baseline_hrv = recovery
        .iter()
        .rev()
        .take(30)
        .filter_map(|r| r.hrv)
        .collect::<Vec<_>>();
    let recent_strain = strain_history
        .iter()
        .rev()
        .take(7)
        .map(|d| d.strain_score)
        .collect::<Vec<_>>();
    let baseline_strain = strain_history
        .iter()
        .rev()
        .take(30)
        .map(|d| d.strain_score)
        .collect::<Vec<_>>();
    let recent_stress = recovery
        .iter()
        .rev()
        .take(7)
        .filter_map(|r| {
            r.resting_hr
                .zip(r.hrv)
                .map(|(rhr, hrv)| rhr.max(1.0) / hrv.max(1.0) * 50.0)
        })
        .collect::<Vec<_>>();
    let baseline_stress = recovery
        .iter()
        .rev()
        .take(30)
        .filter_map(|r| {
            r.resting_hr
                .zip(r.hrv)
                .map(|(rhr, hrv)| rhr.max(1.0) / hrv.max(1.0) * 50.0)
        })
        .collect::<Vec<_>>();

    TrendReport {
        recovery_trend: trend_metric(
            recent_average(&recent_recovery).unwrap_or(recovery_score),
            recent_average(&baseline_recovery).unwrap_or(recovery_score),
            false,
        ),
        hrv_trend: trend_metric(
            recent_average(&recent_hrv).unwrap_or(0.0),
            recent_average(&baseline_hrv).unwrap_or(0.0),
            false,
        ),
        sleep_trend: trend_metric(sleep_score, sleep_score, false),
        training_trend: trend_metric(
            recent_average(&recent_strain).unwrap_or(0.0),
            recent_average(&baseline_strain).unwrap_or(0.0),
            false,
        ),
        stress_trend: trend_metric(
            recent_average(&recent_stress).unwrap_or(0.0),
            recent_average(&baseline_stress).unwrap_or(0.0),
            true,
        ),
    }
}

#[derive(Debug, Clone, Default)]
struct RecoveryHistoryDay {
    resting_hr: Option<f64>,
    hrv: Option<f64>,
    temp: Option<f64>,
    recovery_score: Option<f64>,
}

fn recovery_history(store: &GrapeStore) -> Option<Vec<RecoveryHistoryDay>> {
    let mut stmt = store
        .conn
        .prepare(
            r#"
        SELECT resting_hr_bpm, hrv_rmssd_ms, skin_temperature_delta_c
        FROM daily_recovery_metrics
        WHERE source_kind != 'unavailable'
        ORDER BY start_time_unix_ms ASC
        "#,
        )
        .ok()?;
    let rows = stmt
        .query_map([], |row| {
            let resting_hr = row.get::<_, Option<f64>>(0)?;
            let hrv = row.get::<_, Option<f64>>(1)?;
            let temp = row.get::<_, Option<f64>>(2)?;
            let recovery_score = match (resting_hr, hrv) {
                (Some(rhr), Some(hrv)) => {
                    Some((70.0 + (hrv - 65.0) * 0.4 + (60.0 - rhr) * 2.0).clamp(0.0, 100.0))
                }
                _ => None,
            };
            Ok(RecoveryHistoryDay {
                resting_hr,
                hrv,
                temp,
                recovery_score,
            })
        })
        .ok()?;
    rows.collect::<Result<Vec<_>, _>>().ok()
}

fn baseline_window_report(
    recovery: &[RecoveryHistoryDay],
    strain_history: &[DerivedStrainDay],
    limit: usize,
) -> BaselineWindowReport {
    let recovery_iter = recovery.iter().rev().take(limit);
    let hrv = recovery_iter
        .clone()
        .filter_map(|d| d.hrv)
        .collect::<Vec<_>>();
    let rhr = recovery_iter
        .clone()
        .filter_map(|d| d.resting_hr)
        .collect::<Vec<_>>();
    let temp = recovery_iter
        .clone()
        .filter_map(|d| d.temp)
        .collect::<Vec<_>>();
    let rec = recovery_iter
        .filter_map(|d| d.recovery_score)
        .collect::<Vec<_>>();
    let strain = strain_history
        .iter()
        .rev()
        .take(limit)
        .map(|d| d.strain_score)
        .collect::<Vec<_>>();
    BaselineWindowReport {
        day_count: recovery.len().min(limit).max(strain.len().min(limit)) as u32,
        baseline_hrv: round_1(recent_average(&hrv).unwrap_or(0.0)),
        baseline_rhr: round_1(recent_average(&rhr).unwrap_or(0.0)),
        baseline_temp: round_1(recent_average(&temp).unwrap_or(0.0)),
        baseline_sleep: round_1(recent_average(&rec).unwrap_or(0.0)),
        baseline_strain: round_1(recent_average(&strain).unwrap_or(0.0)),
    }
}

fn trend_metric(current: f64, baseline: f64, lower_is_better: bool) -> TrendMetricReport {
    let delta = current - baseline;
    let useful_delta = if lower_is_better { -delta } else { delta };
    let direction = if useful_delta.abs() < 0.5 {
        "FLAT"
    } else if useful_delta > 0.0 {
        "UP"
    } else {
        "DOWN"
    };
    TrendMetricReport {
        current: round_1(current),
        baseline: round_1(baseline),
        delta: round_1(delta),
        direction: direction.to_string(),
    }
}

fn estimated_hr_zone_minutes(
    duration_minutes: f64,
    average_hr: f64,
    resting_hr: f64,
    max_hr: f64,
) -> Vec<f64> {
    let reserve_fraction =
        ((average_hr - resting_hr) / (max_hr - resting_hr).max(1.0)).clamp(0.0, 1.0);
    let center = (reserve_fraction * 4.0).round() as usize;
    let mut zones = vec![0.0; 5];
    for (index, zone) in zones.iter_mut().enumerate() {
        let distance = (index as i32 - center as i32).abs() as f64;
        *zone = (1.0 / (1.0 + distance * 1.8)).max(0.05);
    }
    let total = zones.iter().sum::<f64>().max(1.0);
    zones
        .iter_mut()
        .for_each(|zone| *zone = *zone / total * duration_minutes);
    zones
}

fn strain_intensity_class(score: f64) -> &'static str {
    if score < 5.0 {
        "Light"
    } else if score < 10.0 {
        "Moderate"
    } else if score < 15.0 {
        "Hard"
    } else if score < 18.0 {
        "Very Hard"
    } else {
        "Extreme"
    }
}

fn json_f64(value: &Value, key: &str) -> Option<f64> {
    value
        .get(key)
        .and_then(Value::as_f64)
        .filter(|v| v.is_finite())
}

fn json_f64_array(value: &Value, key: &str) -> Option<Vec<f64>> {
    value
        .get(key)?
        .as_array()?
        .iter()
        .map(Value::as_f64)
        .collect::<Option<Vec<_>>>()
        .filter(|values| {
            values
                .iter()
                .all(|value| value.is_finite() && *value >= 0.0)
        })
}

fn recent_average(values: &[f64]) -> Option<f64> {
    if values.is_empty() {
        None
    } else {
        Some(values.iter().sum::<f64>() / values.len() as f64)
    }
}

fn round_1(value: f64) -> f64 {
    (value * 10.0).round() / 10.0
}

pub fn insert_packet(database_path: String, frame_hex: String, device_type: String) -> String {
    let mutex = get_device_state_mutex();
    let mut state = mutex.lock().unwrap();
    state.packets_received += 1;

    // Check special frames used to feed simple realtime status
    if frame_hex.starts_with("HR:") {
        if let Ok(hr) = frame_hex[3..].parse::<i32>() {
            state.heart_rate = hr;
        }
        return serde_json::to_string(&*state).unwrap();
    } else if frame_hex.starts_with("BAT:") {
        if let Ok(bat) = frame_hex[4..].parse::<i32>() {
            state.battery = bat;
        }
        return serde_json::to_string(&*state).unwrap();
    } else if frame_hex.starts_with("CONN:") {
        state.connection_state = frame_hex[5..].to_string();
        return serde_json::to_string(&*state).unwrap();
    }

    // Try parsing as hex and inserting
    let dev_type = match device_type.to_lowercase().as_str() {
        "maverick" | "gen4" | "whoop4" => DeviceType::Maverick,
        _ => DeviceType::Puffin,
    };

    let store_result = GrapeStore::open(Path::new(&database_path));
    match store_result {
        Ok(store) => {
            let evidence_id = format!("live-{}", uuid_simple());

            let current_time: String = store
                .conn
                .query_row("SELECT strftime('%Y-%m-%dT%H:%M:%fZ', 'now')", [], |row| {
                    row.get(0)
                })
                .unwrap_or_else(|_| "2026-06-17T14:10:00.000Z".to_string());

            let input = CapturedFrameInput {
                evidence_id,
                frame_id: None,
                source: "live-android".to_string(),
                captured_at: current_time,
                device_model: match dev_type {
                    DeviceType::Maverick => "WHOOP Strap 4.0".to_string(),
                    _ => "WHOOP Strap 5.0".to_string(),
                },
                frame_hex: frame_hex.clone(),
                sensitivity: "standard".to_string(),
                capture_session_id: None,
                device_type: dev_type,
            };

            let import_res = import_captured_frame_batch_with_output_options(
                &store,
                &[input],
                CapturedFrameBatchOptions {
                    parser_version: "live-notification",
                },
                CapturedFrameBatchOutputOptions::default(),
            );

            match import_res {
                Ok(_) => {
                    state.frames_parsed += 1;
                    serde_json::to_string(&*state).unwrap()
                }
                Err(err) => {
                    format!("{{\"error\": \"import failed: {}\"}}", err)
                }
            }
        }
        Err(err) => {
            format!("{{\"error\": \"failed to open database: {}\"}}", err)
        }
    }
}

pub fn get_device_state(_database_path: String) -> String {
    let mutex = get_device_state_mutex();
    let state = mutex.lock().unwrap();
    serde_json::to_string(&*state).unwrap()
}

pub fn build_command_frame(sequence: u8, command: u8, data: Vec<u8>) -> Vec<u8> {
    crate::protocol::build_v5_command_frame(sequence, command, &data)
}

fn uuid_simple() -> String {
    use sha2::{Digest, Sha256};
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let mut hasher = Sha256::new();
    hasher.update(ts.to_string());
    hex::encode(&hasher.finalize()[..8])
}
