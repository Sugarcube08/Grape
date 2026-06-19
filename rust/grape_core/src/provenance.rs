#![allow(dead_code)]

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq, Eq)]
pub enum DataOrigin {
    Measured,
    Derived,
    Estimated,
    Imported,
    Unavailable,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Metric<T> {
    pub value: Option<T>,
    pub origin: DataOrigin,
    pub confidence: f32,
    pub timestamp: i64,
    pub source: String,
    pub baseline: String,
}

impl<T> Metric<T> {
    pub fn new(value: Option<T>, origin: DataOrigin, confidence: f32, timestamp: i64, source: String, baseline: String) -> Self {
        Self {
            value,
            origin,
            confidence,
            timestamp,
            source,
            baseline,
        }
    }

    pub fn unavailable() -> Self {
        Self {
            value: None,
            origin: DataOrigin::Unavailable,
            confidence: 0.0,
            timestamp: 0,
            source: "none".to_string(),
            baseline: "none".to_string(),
        }
    }
}