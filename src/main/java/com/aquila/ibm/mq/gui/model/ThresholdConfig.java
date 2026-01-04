package com.aquila.ibm.mq.gui.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThresholdConfig {
    private String queueName;
    private int warningThreshold;
    private int criticalThreshold;
    private boolean warningThresholdPercentage;
    private boolean criticalThresholdPercentage;
    private boolean enabled;

    public ThresholdConfig() {
        this.enabled = true;
        this.warningThresholdPercentage = true;
        this.criticalThresholdPercentage = true;
        this.warningThreshold = 70;
        this.criticalThreshold = 90;
    }

    public ThresholdConfig(String queueName, int warningThreshold, int criticalThreshold) {
        this.queueName = queueName;
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
        this.enabled = true;
        this.warningThresholdPercentage = true;
        this.criticalThresholdPercentage = true;
    }

    public enum AlertLevel {
        NONE,
        WARNING,
        CRITICAL
    }

    public AlertLevel getAlertLevel(QueueInfo queueInfo) {
        if (!enabled) return AlertLevel.NONE;

        int depth = queueInfo.getCurrentDepth();
        int maxDepth = queueInfo.getMaxDepth();

        int criticalValue = criticalThresholdPercentage ?
            (int)(maxDepth * criticalThreshold / 100.0) : criticalThreshold;
        int warningValue = warningThresholdPercentage ?
            (int)(maxDepth * warningThreshold / 100.0) : warningThreshold;

        if (depth >= criticalValue) {
            return AlertLevel.CRITICAL;
        } else if (depth >= warningValue) {
            return AlertLevel.WARNING;
        }
        return AlertLevel.NONE;
    }
}
