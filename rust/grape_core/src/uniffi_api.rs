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
    format!("{{\"status\": \"ignored\", \"database_path\": \"{}\"}}", database_path)
}

pub fn compute_recovery(database_path: String) -> String {
    format!("{{\"status\": \"ignored\", \"database_path\": \"{}\"}}", database_path)
}

pub fn compute_strain(database_path: String) -> String {
    format!("{{\"status\": \"ignored\", \"database_path\": \"{}\"}}", database_path)
}

pub fn compute_stress(database_path: String) -> String {
    format!("{{\"status\": \"ignored\", \"database_path\": \"{}\"}}", database_path)
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


