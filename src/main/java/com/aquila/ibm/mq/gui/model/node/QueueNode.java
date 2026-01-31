/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Configuration model for a queue browser node.
 * Stores queue manager reference, filter pattern, request mode,
 * and queue descriptions. Supports JSON persistence.
 */
package com.aquila.ibm.mq.gui.model.node;

import com.aquila.ibm.mq.gui.config.Configuration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Map;

@Slf4j
@Getter
@ToString
@Builder
public class QueueNode {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public record QueueDescription(String label) {
    }

    private String queueManager;
    /**
     * Sequential queue request flag.
     * When true, queue information is requested sequentially instead of in parallel.
     * Default: false (parallel requests)
     */
    @lombok.Builder.Default
    private boolean sequencialQueueRequest = false;
    /**
     * Queue filter pattern. Supports both:
     * - IBM MQ wildcards: * (any chars), ? (single char) - e.g., "DEV.*"
     * - Java regex patterns: full regex syntax - e.g., "QUEUE\\.(ONE|TWO)"
     * Default: "*" (all queues)
     */
    private String queuePattern;
    private Map<String, QueueDescription> descriptions;

    /**
     * Read QueueNode from the file <strong>key.json</strong>
     * @param key
     * @param label
     * @param queueManager
     * @return
     */
    public static QueueNode fromFile(String key,  String queueManager) {
        final File file = new File(Configuration.CONFIG_DIR, String.format("%s.json", key));
        try (Reader reader = new FileReader(file)) {
            final QueueNode queueNode = gson.fromJson(reader, QueueNode.class);
            log.info("QueueNode:\n{}", gson.toJson(queueNode));
            return queueNode;
        } catch (IOException e) {
            log.error("Failed to load QueueNode: {}", key, e);
            QueueNode queueNode = QueueNode.builder()
                    .queueManager(queueManager)
                    .queuePattern("*")
                    .build();
            queueNode.save(key);
            return queueNode;
        }
    }

    public void save(String key) {
        String filename = String.format("%s.json", key);
        final File file = new File(Configuration.CONFIG_DIR, filename);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(this, writer);
            log.info("Saved QueueNode: {}", file);
        } catch (IOException e1) {
            log.error("Failed to save QueueNode", e1);
        }
    }

}
