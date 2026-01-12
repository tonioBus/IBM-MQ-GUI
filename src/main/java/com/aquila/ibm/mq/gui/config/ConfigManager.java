package com.aquila.ibm.mq.gui.config;

import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.model.QueueBrowserConfig;
import com.aquila.ibm.mq.gui.model.QueueManagerConfig;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ConfigManager {
    public static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".ibmmqgui";
    private static final String CONNECTIONS_FILE = "connections.json";
    private static final String THRESHOLDS_FILE = "thresholds.json";
    private static final String HIERARCHY_FILE = "hierarchy.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, QueueManagerConfig> queueManagers = loadConnections();
    @Getter
    private Map<String, QueueBrowserConfig> queueBrowserConfigMap = new HashMap<>();

    public ConfigManager() {
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

        try (Reader reader = new FileReader(file)) {
            final Type mapType = new TypeToken<Map<String, QueueManagerConfig>>() {
            }.getType();
            queueManagers = gson.fromJson(reader, mapType);
            log.info("Loaded {} connection(s)", queueManagers != null ? queueManagers.size() : 0);
            return queueManagers != null ? queueManagers : new HashMap<>();
        } catch (IOException e) {
            log.error("Failed to load connections", e);
            return new HashMap<>();
        }
    }

    public void saveConnections(Map<String, QueueManagerConfig> connections) {
        final File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(connections, writer);
            log.info("Saved {} connection(s)", connections.size());
        } catch (IOException e) {
            log.error("Failed to save connections", e);
        }
    }

    public void saveConnection(QueueManagerConfig connection) {
        boolean updated = false;
        final String label = connection.getLabel();
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
            final Type mapType = new TypeToken<Map<String, ThresholdConfig>>() {
            }.getType();
            final Map<String, ThresholdConfig> thresholds = gson.fromJson(reader, mapType);
            log.info("Loaded {} threshold(s)", thresholds != null ? thresholds.size() : 0);
            return thresholds != null ? thresholds : new HashMap<>();
        } catch (IOException e) {
            log.error("Failed to load thresholds", e);
            return new HashMap<>();
        }
    }

    public void saveThresholds(Map<String, ThresholdConfig> thresholds) {
        final File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(thresholds, writer);
            log.info("Saved {} threshold(s)", thresholds.size());
        } catch (IOException e) {
            log.error("Failed to save thresholds", e);
        }
    }

    public void saveThreshold(String queueName, ThresholdConfig threshold) {
        final Map<String, ThresholdConfig> thresholds = loadThresholds();
        thresholds.put(queueName, threshold);
        saveThresholds(thresholds);
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

        try (Reader reader = new FileReader(file)) {
            final HierarchyConfig hierarchyConfig = gson.fromJson(reader, HierarchyConfig.class);
            log.info("Loaded hierarchy with {} nodes", hierarchyConfig != null ? hierarchyConfig.getNodes().size() : 0);
            if (!this.queueManagers.isEmpty() && hierarchyConfig != null && hierarchyConfig.getNodes() != null) {
                hierarchyConfig.getNodes().entrySet().parallelStream()
                        .filter(entry -> entry.getValue().isQueue())
                        .forEach(entry -> {
                            final String key = entry.getKey();
                            final QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.fromFile(key,
                                    this.queueManagers.values().stream().toList().get(0).getQueueManager());
                            hierarchyConfig.getNode(key).setQueueBrowserConfig(queueBrowserConfig);
                            this.queueBrowserConfigMap.put(key, queueBrowserConfig);
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
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(hierarchyConfig, writer);
            log.info("Saved hierarchy with {} nodes", hierarchyConfig.getNodes().size());
        } catch (IOException e) {
            log.error("Failed to save hierarchy", e);
        }
        if (!this.queueManagers.isEmpty() && hierarchyConfig.getNodes() != null) {
            hierarchyConfig.getNodes().entrySet().parallelStream().forEach(entry -> {
                final String key = entry.getKey();
                final QueueBrowserConfig queueBrowserConfig = this.queueBrowserConfigMap.get(key);
                if (queueBrowserConfig != null) save(key, queueBrowserConfig);
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
        final HierarchyConfig hierarchy = new HierarchyConfig();

//        logger.info("Creating default hierarchy from {} connections", connections.size());
//
//        for (ConnectionConfig config : connections) {
//            final String displayName = config.getName() != null && !config.getName().isEmpty()
//                    ? config.getName()
//                    : config.getQueueManager() + "@" + config.getHost();
//
//            final HierarchyNode node = new HierarchyNode(
//                    HierarchyNode.NodeType.BROWSER,
//                    displayName,
//                    config.getName()
//            );
//
//            hierarchy.addNode(node, null);  // Add to root level
//        }
//
//        logger.info("Created default hierarchy with {} queue managers", connections.size());
        return hierarchy;
    }

    public void save(String id, QueueBrowserConfig queueBrowserConfig) {
        log.info("Save QueueBrowserConfig: {} -> \n{}", id, queueBrowserConfig);
        queueBrowserConfig.save(id);
    }

}
