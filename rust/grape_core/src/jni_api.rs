#![allow(unsafe_attr_outside_unsafe)]

use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jstring, jdouble, jbyte, jbyteArray};

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_computeSleep(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::compute_sleep(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_computeRecovery(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::compute_recovery(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_computeStrain(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::compute_strain(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_computeStress(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::compute_stress(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_insertPacket(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
    frame_hex: JString,
    device_type: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let fh: String = match env.get_string(&frame_hex) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let dt: String = match env.get_string(&device_type) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::insert_packet(db_path, fh, dt);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_getDeviceState(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::get_device_state(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_jniComputeSleepV1(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    if let Some(report) = crate::uniffi_api::compute_sleep_v1(db_path) {
        if let Ok(json) = serde_json::to_string(&report) {
            if let Ok(s) = env.new_string(json) {
                return s.into_raw();
            }
        }
    }
    std::ptr::null_mut()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_sleepSummary(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::sleep_summary(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_sleepStageDistribution(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let response = crate::uniffi_api::sleep_stage_distribution(db_path);
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_sleepEfficiency(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jdouble {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return 0.0,
    };
    crate::uniffi_api::sleep_efficiency(db_path)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_sleepDebt(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jdouble {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return 0.0,
    };
    crate::uniffi_api::sleep_debt(db_path)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_jniComputeRecoveryV0(
    mut env: JNIEnv,
    _class: JClass,
    database_path: JString,
) -> jstring {
    let db_path: String = match env.get_string(&database_path) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    if let Some(report) = crate::uniffi_api::compute_recovery_v0(db_path) {
        if let Ok(json) = serde_json::to_string(&report) {
            if let Ok(s) = env.new_string(json) {
                return s.into_raw();
            }
        }
    }
    std::ptr::null_mut()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_buildCommandFrame(
    env: JNIEnv,
    _class: JClass,
    sequence: jbyte,
    command: jbyte,
    data: JByteArray,
) -> jbyteArray {
    let byte_vec = match env.convert_byte_array(&data) {
        Ok(bytes) => bytes,
        Err(_) => return std::ptr::null_mut(),
    };
    let out_vec = crate::uniffi_api::build_command_frame(sequence as u8, command as u8, byte_vec);
    match env.byte_array_from_slice(&out_vec) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_uniffi_grape_GrapeJni_getCoreMetadata(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let response = "{\"version\":\"0.3.0-alpha\",\"git_commit\":\"6e7ab1f\",\"build_date\":\"2026-06-19\",\"schema_version\":12}".to_string();
    match env.new_string(response) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
