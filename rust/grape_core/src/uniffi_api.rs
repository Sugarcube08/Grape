use std::sync::Mutex;
use std::sync::OnceLock;
use serde::{Serialize, Deserialize};
use std::path::Path;
use crate::store::GrapeStore;
use crate::capture_import::{import_captured_frame_batch_with_output_options, CapturedFrameInput, CapturedFrameBatchOptions, CapturedFrameBatchOutputOptions};
use crate::protocol::DeviceType;

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
    format!("{{\"status\": \"ignored\", \"database_path\": \"{}\"}}", database_path)
}

pub fn compute_stress(database_path: String) -> String {
    format!("{{\"status\": \"ignored\", \"database_path\": \"{}\"}}", database_path)
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

pub fn compute_sleep_v1(database_path: String) -> Option<SleepReport> {
    let store = GrapeStore::open(Path::new(&database_path)).ok()?;
    
    // Find the latest external sleep session
    let mut stmt = store.conn.prepare(
        "SELECT start_time_unix_ms, end_time_unix_ms FROM external_sleep_sessions ORDER BY start_time_unix_ms DESC LIMIT 1"
    ).ok()?;
    
    let (start_unix_ms, end_unix_ms): (i64, i64) = stmt.query_row([], |row| {
        Ok((row.get(0)?, row.get(1)?))
    }).ok()?;

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
    ).ok()?;

    let sleep_input = report.sleep_input.as_ref()?;

    let sleep_v1_input = crate::bridge::sleep_v1_input_from_feature_score(
        &store,
        sleep_input,
        &report,
        false,
    ).ok()?;

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
    compute_sleep_v1(database_path).map(|r| r.efficiency / 100.0).unwrap_or(0.0)
}

pub fn sleep_debt(database_path: String) -> f64 {
    compute_sleep_v1(database_path).map(|r| r.debt).unwrap_or(0.0)
}

pub fn compute_recovery_v0(database_path: String) -> Option<RecoveryReport> {
    let store = GrapeStore::open(Path::new(&database_path)).ok()?;
    
    // Fetch baselines
    let hrv_baseline_rmssd_ms = store.conn.query_row(
        "SELECT AVG(hrv_rmssd_ms) FROM daily_recovery_metrics WHERE hrv_rmssd_ms IS NOT NULL",
        [],
        |row| row.get::<_, f64>(0)
    ).unwrap_or(65.0);

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

    let (start_time_unix_ms, end_time_unix_ms, resting_hr_bpm, hrv_rmssd_ms, respiratory_rate_rpm, skin_temp_delta_c) = match stmt.query_row([], |row| {
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

    let sleep_score_0_to_100 = compute_sleep_v1(database_path).map(|r| r.score).unwrap_or(75.0);

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
            
            let current_time: String = store.conn.query_row(
                "SELECT strftime('%Y-%m-%dT%H:%M:%fZ', 'now')",
                [],
                |row| row.get(0)
            ).unwrap_or_else(|_| "2026-06-17T14:10:00.000Z".to_string());

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
    use sha2::{Sha256, Digest};
    let ts = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let mut hasher = Sha256::new();
    hasher.update(ts.to_string());
    hex::encode(&hasher.finalize()[..8])
}


