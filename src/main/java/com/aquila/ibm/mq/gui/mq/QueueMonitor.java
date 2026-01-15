package com.aquila.ibm.mq.gui.mq;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class QueueMonitor extends Thread {
    private final QueueService queueService;
    private final AlertManager alertManager;
    private final List<QueueInfo> monitoredQueues;
    private final AtomicBoolean running;
    private final AtomicBoolean paused;
    private int refreshInterval = 5000;
    private QueueMonitorListener listener;

    public QueueMonitor(QueueService queueService, AlertManager alertManager) {
        super("QueueMonitor");
        this.queueService = queueService;
        this.alertManager = alertManager;
        this.monitoredQueues = new CopyOnWriteArrayList<>();
        this.running = new AtomicBoolean(false);
        this.paused = new AtomicBoolean(false);
        setDaemon(true);
    }

    @Override
    public void run() {
        running.set(true);
        log.info("Queue monitor started");

        while (running.get()) {
            try {
                if (!paused.get() && !monitoredQueues.isEmpty()) {
                    log.info("updateQueues();");
                    // updateQueues();
                }
                Thread.sleep(refreshInterval);
            } catch (InterruptedException e) {
                log.info("Queue monitor interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in queue monitor", e);
            }
        }

        log.info("Queue monitor stopped");
    }

    private void updateQueues() {
        try {
            queueService.refreshAllQueues(monitoredQueues);

            for (QueueInfo queue : monitoredQueues) {
                alertManager.checkQueue(queue);
            }

            if (listener != null) {
                listener.onQueuesUpdated(monitoredQueues);
            }
        } catch (Exception e) {
            log.error("Error updating queues", e);
            if (listener != null) {
                listener.onMonitorError(e);
            }
        }
    }

    public void setMonitoredQueues(List<QueueInfo> queues) {
        monitoredQueues.clear();
        monitoredQueues.addAll(queues);
        log.info("Monitoring {} queues", queues.size());
    }

    public void addQueue(QueueInfo queue) {
        if (!monitoredQueues.contains(queue)) {
            monitoredQueues.add(queue);
            log.info("Added queue to monitoring: {}", queue.getQueue());
        }
    }

    public void removeQueue(QueueInfo queue) {
        monitoredQueues.remove(queue);
        log.info("Removed queue from monitoring: {}", queue.getQueue());
    }

    public void pauseMonitoring() {
        paused.set(true);
        log.info("Queue monitoring paused");
    }

    public void resumeMonitoring() {
        paused.set(false);
        log.info("Queue monitoring resumed");
    }

    public void stopMonitoring() {
        running.set(false);
        interrupt();
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public int getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(int refreshInterval) {
        this.refreshInterval = Math.max(1000, Math.min(60000, refreshInterval));
        log.info("Refresh interval set to {} ms", this.refreshInterval);
    }

    public void setListener(QueueMonitorListener listener) {
        this.listener = listener;
    }

    public interface QueueMonitorListener {
        void onQueuesUpdated(List<QueueInfo> queues);
        void onMonitorError(Exception e);
    }
}
