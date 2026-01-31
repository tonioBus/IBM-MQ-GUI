/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Central configuration manager for the application.
 * Handles persistence of connection profiles, thresholds, hierarchy,
 * and message templates to JSON files in the user's home directory.
 */
package com.aquila.ibm.mq.gui.config;

import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.model.MessageTemplate;
import com.aquila.ibm.mq.gui.model.QueueManagerConfig;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import com.aquila.ibm.mq.gui.model.node.QueueNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Configuration {
    public static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".ibmmqgui";
    private static final String CONNECTIONS_FILE = "connections.json";
    private static final String THRESHOLDS_FILE = "thresholds.json";
    private static final String HIERARCHY_FILE = "hierarchy.json";
    private static final String MESSAGE_TEMPLATES_FILE = "message_templates.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, QueueManagerConfig> queueManagers = loadConnections();
    @Getter
    private Map<String, QueueNode> queueBrowserConfigMap = new HashMap<>();

    public Configuration() {
        initConfigDirectory();
    }

    private void initConfigDirectory() {
        try {
            final Path configPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                log.info("Created config directory: {}", CONFIG_DIR);
            }
        } catch (IOException e) {
            log.error("Failed to create config directory", e);
        }
    }

    public Map<String, QueueManagerConfig> loadConnections() {
        final File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        if (!file.exists()) {
            log.info("No connections file found, returning empty list");
            return new HashMap<>();
        }
        final JavaType javaType = objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, QueueManagerConfig.class);
        try {
            return objectMapper.readValue(file, javaType);
        } catch (IOException e) {
            log.error("Failed to load connections", e);
            return new HashMap<>();
        }
    }

    public void saveConnections(Map<String, QueueManagerConfig> connections) {
        final File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, connections);
            log.info("Saved {} connection(s)", connections.size());
        } catch (IOException e) {
            log.error("Failed to save connections", e);
        }
    }

    public void saveConnection(QueueManagerConfig connection) {
        boolean updated = false;
        final String label = connection.toString();
        if (!queueManagers.containsKey(label)) {
            queueManagers.put(label, connection);
            updated = true;
        }
        if (updated) {
            saveConnections(queueManagers);
        }
    }

    public void deleteConnection(String name) {
        queueManagers.remove(name);
        saveConnections(queueManagers);
        log.info("Deleted connection: {}", name);
    }

    public Map<String, ThresholdConfig> loadThresholds() {
        final File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        if (!file.exists()) {
            log.info("No thresholds file found, returning empty map");
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(file)) {
            final JavaType javaType = objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, ThresholdConfig.class);
            final Map<String, ThresholdConfig> thresholds = objectMapper.readValue(reader, javaType);
            log.info("Loaded {} threshold(s)", thresholds != null ? thresholds.size() : 0);
            return thresholds != null ? thresholds : new HashMap<>();
        } catch (IOException e) {
            log.error("Failed to load thresholds", e);
            return new HashMap<>();
        }
    }

    public void saveThresholds(Map<String, ThresholdConfig> thresholds) {
        final File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, thresholds);
            log.info("Saved {} threshold(s)", thresholds.size());
        } catch (IOException e) {
            log.error("Failed to save thresholds", e);
        }
    }

    public ThresholdConfig getThreshold(String queueName) {
        final Map<String, ThresholdConfig> thresholds = loadThresholds();
        return thresholds.getOrDefault(queueName, new ThresholdConfig());
    }

    /**
     * Load the queue manager hierarchy from JSON file.
     *
     * @return HierarchyConfig object, or null if file doesn't exist
     */
    public HierarchyConfig loadHierarchy() {
        final File file = new File(CONFIG_DIR, HIERARCHY_FILE);
        log.info("load hierarchy: {}", file);
        if (!file.exists()) {
            log.info("No hierarchy file found");
            return null;
        }

        try {
            final HierarchyConfig hierarchyConfig = objectMapper.readValue(file, HierarchyConfig.class);
            log.info("Loaded hierarchy with {} nodes", hierarchyConfig != null ? hierarchyConfig.getNodes().size() : 0);
            if (!this.queueManagers.isEmpty() && hierarchyConfig != null && hierarchyConfig.getNodes() != null) {
                hierarchyConfig.getNodes().entrySet().parallelStream()
                        .filter(entry -> entry.getValue().isQueue())
                        .forEach(entry -> {
                            final String key = entry.getKey();
                            final QueueNode queueNode = QueueNode.fromFile(key,
                                    this.queueManagers.values().stream().toList().getFirst().getQueueManager());
                            hierarchyConfig.getNode(key).setQueueNode(queueNode);
                            this.queueBrowserConfigMap.put(key, queueNode);
                        });
            }
            return hierarchyConfig;
        } catch (IOException e) {
            log.error("Failed to load hierarchy", e);
            return null;
        }
    }

    /**
     * Save the queue manager hierarchy to JSON file.
     *
     * @param hierarchyConfig The hierarchy configuration to save
     */
    public void saveHierarchy(HierarchyConfig hierarchyConfig) {
        if (hierarchyConfig == null) {
            log.warn("Attempted to save null hierarchy");
            return;
        }
        final File file = new File(CONFIG_DIR, HIERARCHY_FILE);
        // Create backup of existing file
        if (file.exists()) {
            final File backup = new File(CONFIG_DIR, HIERARCHY_FILE + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn("Failed to create backup of hierarchy file", e);
            }
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, hierarchyConfig);
            log.info("Saved hierarchy with {} nodes", hierarchyConfig.getNodes().size());
        } catch (IOException e) {
            log.error("Failed to save hierarchy", e);
        }
        if (!this.queueManagers.isEmpty() && hierarchyConfig.getNodes() != null) {
            hierarchyConfig.getNodes().entrySet().parallelStream().forEach(entry -> {
                final String key = entry.getKey();
                final QueueNode queueNode = this.queueBrowserConfigMap.get(key);
                if (queueNode != null) save(key, queueNode);
            });
        }
    }

    /**
     * Create a default hierarchy from existing connections.
     * All connections are placed at root level (no folders).
     *
     * @param connections List of connection configurations
     * @return New HierarchyConfig with all connections at root
     */
    public HierarchyConfig createDefaultHierarchy(Map<String, QueueManagerConfig> connections) {
        return new HierarchyConfig();
    }

    public void save(String id, QueueNode queueNode) {
        log.info("Save QueueNode: {} -> \n{}", id, queueNode);
        queueNode.save(id);
    }

    public Map<String, MessageTemplate> loadMessageTemplates() {
        final File file = new File(CONFIG_DIR, MESSAGE_TEMPLATES_FILE);
        if (!file.exists()) {
            log.info("No message templates file found, returning empty map");
            return new HashMap<>();
        }

        try {
            final JavaType javaType = objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, MessageTemplate.class);
            final Map<String, MessageTemplate> templates = objectMapper.readValue(file, javaType);
            log.info("Loaded {} message template(s)", templates != null ? templates.size() : 0);
            return templates != null ? templates : new HashMap<>();
        } catch (IOException e) {
            log.error("Failed to load message templates", e);
            return new HashMap<>();
        }
    }

    public void saveMessageTemplates(Map<String, MessageTemplate> templates) {
        final File file = new File(CONFIG_DIR, MESSAGE_TEMPLATES_FILE);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, templates);
            log.info("Saved {} message template(s)", templates.size());
        } catch (IOException e) {
            log.error("Failed to save message templates", e);
        }
    }

    public void saveMessageTemplate(MessageTemplate template) {
        final Map<String, MessageTemplate> templates = loadMessageTemplates();
        templates.put(template.getName(), template);
        saveMessageTemplates(templates);
    }

    public void deleteMessageTemplate(String name) {
        final Map<String, MessageTemplate> templates = loadMessageTemplates();
        templates.remove(name);
        saveMessageTemplates(templates);
        log.info("Deleted message template: {}", name);
    }

    public MessageTemplate getMessageTemplate(String name) {
        final Map<String, MessageTemplate> templates = loadMessageTemplates();
        return templates.get(name);
    }

}

