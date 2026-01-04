package com.aquila.ibm.mq.gui.config;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import com.aquila.ibm.mq.gui.util.SoundPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AlertManager {
    private static final Logger logger = LoggerFactory.getLogger(AlertManager.class);
    private final ConfigManager configManager;
    private final SoundPlayer soundPlayer;
    private final Map<String, ThresholdConfig.AlertLevel> currentAlertLevels;
    private final List<AlertEvent> alertHistory;
    private boolean soundEnabled = true;

    public AlertManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.soundPlayer = new SoundPlayer();
        this.currentAlertLevels = new ConcurrentHashMap<>();
        this.alertHistory = Collections.synchronizedList(new ArrayList<>());
    }

    public ThresholdConfig.AlertLevel checkQueue(QueueInfo queueInfo) {
        ThresholdConfig threshold = configManager.getThreshold(queueInfo.getQueue());
        ThresholdConfig.AlertLevel newLevel = threshold.getAlertLevel(queueInfo);
        ThresholdConfig.AlertLevel currentLevel = currentAlertLevels.getOrDefault(
            queueInfo.getQueue(), ThresholdConfig.AlertLevel.NONE);

        if (newLevel != currentLevel) {
            handleAlertLevelChange(queueInfo, currentLevel, newLevel);
            currentAlertLevels.put(queueInfo.getQueue(), newLevel);
        }

        return newLevel;
    }

    private void handleAlertLevelChange(QueueInfo queueInfo,
                                       ThresholdConfig.AlertLevel oldLevel,
                                       ThresholdConfig.AlertLevel newLevel) {
        logger.info("Alert level changed for queue {}: {} -> {}",
                   queueInfo.getQueue(), oldLevel, newLevel);

        AlertEvent event = new AlertEvent(
            queueInfo.getQueue(),
            queueInfo.getCurrentDepth(),
            queueInfo.getMaxDepth(),
            oldLevel,
            newLevel,
            LocalDateTime.now()
        );
        alertHistory.add(event);

        if (newLevel == ThresholdConfig.AlertLevel.CRITICAL ||
            newLevel == ThresholdConfig.AlertLevel.WARNING) {
            if (soundEnabled) {
                playAlertSound(newLevel);
            }
        }
    }

    private void playAlertSound(ThresholdConfig.AlertLevel level) {
        try {
            if (level == ThresholdConfig.AlertLevel.CRITICAL) {
                soundPlayer.playAlert();
            } else if (level == ThresholdConfig.AlertLevel.WARNING) {
                soundPlayer.playWarning();
            }
        } catch (Exception e) {
            logger.error("Failed to play alert sound", e);
        }
    }

    public ThresholdConfig.AlertLevel getCurrentAlertLevel(String queueName) {
        return currentAlertLevels.getOrDefault(queueName, ThresholdConfig.AlertLevel.NONE);
    }

    public Map<String, ThresholdConfig.AlertLevel> getAllCurrentAlerts() {
        return new HashMap<>(currentAlertLevels);
    }

    public List<AlertEvent> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    public List<AlertEvent> getAlertHistory(String queueName) {
        synchronized (alertHistory) {
            return alertHistory.stream()
                .filter(e -> e.getQueueName().equals(queueName))
                .toList();
        }
    }

    public void clearAlertHistory() {
        alertHistory.clear();
        logger.info("Alert history cleared");
    }

    public void clearQueueAlert(String queueName) {
        currentAlertLevels.remove(queueName);
        logger.info("Cleared alert for queue: {}", queueName);
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
        logger.info("Sound alerts {}", soundEnabled ? "enabled" : "disabled");
    }

    public static class AlertEvent {
        private final String queueName;
        private final int depth;
        private final int maxDepth;
        private final ThresholdConfig.AlertLevel oldLevel;
        private final ThresholdConfig.AlertLevel newLevel;
        private final LocalDateTime timestamp;

        public AlertEvent(String queueName, int depth, int maxDepth,
                         ThresholdConfig.AlertLevel oldLevel,
                         ThresholdConfig.AlertLevel newLevel,
                         LocalDateTime timestamp) {
            this.queueName = queueName;
            this.depth = depth;
            this.maxDepth = maxDepth;
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
            this.timestamp = timestamp;
        }

        public String getQueueName() {
            return queueName;
        }

        public int getDepth() {
            return depth;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public ThresholdConfig.AlertLevel getOldLevel() {
            return oldLevel;
        }

        public ThresholdConfig.AlertLevel getNewLevel() {
            return newLevel;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s -> %s (depth: %d/%d)",
                timestamp, queueName, oldLevel, newLevel, depth, maxDepth);
        }
    }
}
