/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Sequential queue refresh scheduler with configurable delay between PCF requests.
 */
package com.aquila.ibm.mq.gui.util;

import com.aquila.ibm.mq.gui.model.QueueInfo;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.widgets.Display;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Scheduler that continuously refreshes queues one by one with a configurable delay between each request.
 */
@Slf4j
public class SequentialQueueRefreshScheduler {

    private final Display display;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread refreshThread;

    /**
     * -- SETTER --
     *  Set the function that fetches a single queue's information via PCF
     *
     * @param fetcher Function that takes queue name and returns QueueInfo
     */
    @Setter
    private Function<String, QueueInfo> queueFetcher;
    /**
     * -- SETTER --
     *  Set callback for when a queue is successfully updated
     *
     * @param callback Consumer that receives QueueInfo and queue name
     */
    @Setter
    private BiConsumer<QueueInfo, String> updateCallback;
    /**
     * -- SETTER --
     *  Set callback for when an error occurs
     *
     * @param callback Consumer that receives queue name and exception
     */
    @Setter
    private BiConsumer<String, Exception> errorCallback;

    private volatile List<String> queueNames;
    private volatile int delayBetweenRequestsMs = 1000;

    public SequentialQueueRefreshScheduler(Display display) {
        this.display = display;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SequentialQueueRefreshThread");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Start the sequential refresh cycle
     * @param queueNames List of queue names to refresh continuously
     * @param delayBetweenRequestsMs Delay in milliseconds between each PCF request
     */
    public synchronized void start(List<String> queueNames, int delayBetweenRequestsMs) {
        if (running.get()) {
            stop();
        }

        this.queueNames = queueNames;
        this.delayBetweenRequestsMs = delayBetweenRequestsMs;
        running.set(true);

        refreshThread = new Thread(this::refreshLoop, "SequentialQueueRefreshLoop");
        refreshThread.setDaemon(true);
        refreshThread.start();

        log.info("Sequential refresh started: {} queues, {}ms delay between requests",
                queueNames.size(), delayBetweenRequestsMs);
    }

    /**
     * Update the delay between requests without stopping
     * @param delayMs New delay in milliseconds
     */
    public void updateDelay(int delayMs) {
        this.delayBetweenRequestsMs = delayMs;
        log.info("Sequential refresh delay updated to: {}ms", delayMs);
    }

    /**
     * Update the list of queues to refresh without stopping
     * @param queueNames New list of queue names
     */
    public void updateQueueList(List<String> queueNames) {
        this.queueNames = queueNames;
        log.info("Sequential refresh queue list updated: {} queues", queueNames.size());
    }

    /**
     * Stop the sequential refresh cycle
     */
    public synchronized void stop() {
        if (running.get()) {
            running.set(false);
            if (refreshThread != null) {
                refreshThread.interrupt();
                refreshThread = null;
            }
            log.info("Sequential refresh stopped");
        }
    }

    /**
     * Check if scheduler is currently running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Main refresh loop - continuously cycles through queues
     */
    private void refreshLoop() {
        while (running.get()) {
            List<String> currentQueueNames = queueNames;
            log.info("refreshLoop: Starting refresh cycle for {} queues", currentQueueNames != null ? currentQueueNames.size() : 0);
            if (currentQueueNames == null || currentQueueNames.isEmpty()) {
                // No queues to refresh, sleep and check again
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            // Cycle through all queues
            for (String queueName : currentQueueNames) {
                if (!running.get()) {
                    break;
                }

                try {
                    // Fetch queue info via PCF
                    if (queueFetcher != null) {
                        QueueInfo queueInfo = queueFetcher.apply(queueName);

                        // Update UI on SWT thread
                        if (!display.isDisposed() && updateCallback != null) {
                            final String finalQueueName = queueName;
                            display.asyncExec(() -> {
                                if (!display.isDisposed()) {
                                    updateCallback.accept(queueInfo, finalQueueName);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    log.error("Error refreshing queue: {}", queueName, e);

                    // Notify error on SWT thread
                    if (!display.isDisposed() && errorCallback != null) {
                        final String finalQueueName = queueName;
                        display.asyncExec(() -> {
                            if (!display.isDisposed()) {
                                errorCallback.accept(finalQueueName, e);
                            }
                        });
                    }
                }

                // Delay before next request
                if (running.get()) {
                    try {
                        Thread.sleep(delayBetweenRequestsMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Shutdown the scheduler and release resources
     */
    public void shutdown() {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

