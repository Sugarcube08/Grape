#![allow(dead_code)]

use crate::store::GrapeStore;
use std::path::Path;
use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq)]
pub enum TrendDirection {
    Increasing,
    Stable,
    Declining,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct TrendSummary {
    pub average: f64,
    pub slope: f64,
    pub volatility: f64,
    pub consistency: f64,
    pub confidence: f32,
    pub direction: TrendDirection,
}

pub enum MetricType {
    Recovery,
    Sleep,
    Strain,
    Stress,
    HRV,
    RHR,
}

pub fn compute_trend(
    database_path: &str,
    metric_type: MetricType,
    window_days: u32,
) -> Option<TrendSummary> {
    let store = GrapeStore::open(Path::new(database_path)).ok()?;
    
    let query = match metric_type {
        MetricType::Recovery => "SELECT recovery_score FROM daily_recovery_metrics WHERE recovery_score IS NOT NULL ORDER BY start_time_unix_ms DESC LIMIT ?",
        MetricType::Sleep => "SELECT score FROM external_sleep_sessions ORDER BY start_time_unix_ms DESC LIMIT ?",
        MetricType::Strain => "SELECT strain_score FROM daily_activity_metrics ORDER BY start_time_unix_ms DESC LIMIT ?",
        _ => return None,
    };

    let mut stmt = store.conn.prepare(query).ok()?;
    let values: Vec<f64> = stmt.query_map([window_days as i32], |row| {
        row.get::<_, f64>(0)
    }).ok()?.collect::<Result<Vec<_>, _>>().ok()?;

    if values.is_empty() {
        return None;
    }

    let count = values.len() as f64;
    let avg = values.iter().sum::<f64>() / count;
    
    // Linear slope: (last - first) / window
    let slope = if values.len() >= 2 {
        (values[0] - values[values.len()-1]) / count
    } else {
        0.0
    };

    // Volatility: Standard Deviation
    let variance = if values.len() > 1 {
        values.iter().map(|v| (v - avg).powi(2)).sum::<f64>() / (count - 1.0)
    } else {
        0.0
    };
    let volatility = variance.sqrt();

    // Consistency: percentage of values within 1 std dev of average
    let consistent_count = values.iter().filter(|&&v| (v - avg).abs() <= volatility).count();
    let consistency = if values.is_empty() { 0.0 } else { consistent_count as f64 / count };

    let direction = if slope > 0.5 {
        TrendDirection::Increasing
    } else if slope < -0.5 {
        TrendDirection::Declining
    } else {
        TrendDirection::Stable
    };

    Some(TrendSummary {
        average: avg,
        slope,
        volatility,
        consistency,
        confidence: (count / window_days as f64) as f32,
        direction,
    })
}