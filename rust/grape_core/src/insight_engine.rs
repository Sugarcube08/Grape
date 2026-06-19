#![allow(dead_code)]

use crate::trend_engine::{TrendSummary, MetricType};
use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub enum InsightCategory {
    Recovery,
    Sleep,
    Strain,
    Stress,
    Behavior,
    Health,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Insight {
    pub message: String,
    pub importance: f32,
    pub confidence: f32,
    pub category: InsightCategory,
}

pub fn generate_insight(
    metric_type: MetricType,
    trend: &TrendSummary,
    current_value: f64,
    baseline_value: f64,
) -> Option<Insight> {
    let delta = current_value - baseline_value;
    
    match metric_type {
        MetricType::Recovery => {
            if delta > 5.0 {
                Some(Insight {
                    message: format!("Recovery improved by {:.0}% compared to baseline", delta),
                    importance: 0.8,
                    confidence: trend.confidence,
                    category: InsightCategory::Recovery,
                })
            } else if delta < -5.0 {
                Some(Insight {
                    message: format!("Recovery declined by {:.0}% compared to baseline", delta.abs()),
                    importance: 0.9,
                    confidence: trend.confidence,
                    category: InsightCategory::Recovery,
                })
            } else {
                Some(Insight {
                    message: "Recovery remains within normal variation".to_string(),
                    importance: 0.3,
                    confidence: trend.confidence,
                    category: InsightCategory::Recovery,
                })
            }
        }
        MetricType::Sleep => {
            if delta > 10.0 {
                Some(Insight {
                    message: format!("Sleep score increased by {:.0}%", delta),
                    importance: 0.7,
                    confidence: trend.confidence,
                    category: InsightCategory::Sleep,
                })
            } else {
                None
            }
        }
        _ => None,
    }
}