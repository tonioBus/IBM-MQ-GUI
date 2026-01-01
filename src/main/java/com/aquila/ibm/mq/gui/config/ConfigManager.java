package com.aquila.ibm.mq.gui.config;

import com.aquila.ibm.mq.gui.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    public static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".ibmmqgui";
    private static final String CONNECTIONS_FILE = "connections.json";
    private static final String THRESHOLDS_FILE = "thresholds.json";
    private static final String HIERARCHY_FILE = "hierarchy.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private List<ConnectionConfig> connections = loadConnections();

    public ConfigManager() {
        initConfigDirectory();
    }

    private void initConfigDirectory() {
        try {
            final Path configPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                logger.info("Created config directory: {}", CONFIG_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
    }

    public List<ConnectionConfig> loadConnections() {
        final File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        if (!file.exists()) {
            logger.info("No connections file found, returning empty list");
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            final Type listType = new TypeToken<List<ConnectionConfig>>() {
            }.getType();
            connections = gson.fromJson(reader, listType);
            logger.info("Loaded {} connection(s)", connections != null ? connections.size() : 0);
            return connections != null ? connections : new ArrayList<>();
        } catch (IOException e) {
            logger.error("Failed to load connections", e);
            return new ArrayList<>();
        }
    }

    public void saveConnections(List<ConnectionConfig> connections) {
        final File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(connections, writer);
            logger.info("Saved {} connection(s)", connections.size());
        } catch (IOException e) {
            logger.error("Failed to save connections", e);
        }
    }

    public void saveConnection(ConnectionConfig connection) {
        boolean updated = false;
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).getName().equals(connection.getName())) {
                connections.set(i, connection);
                updated = true;
                break;
            }
        }

        if (!updated) {
            connections.add(connection);
        }

        saveConnections(connections);
    }

    public void deleteConnection(String name) {
        connections.removeIf(c -> c.getName().equals(name));
        saveConnections(connections);
        logger.info("Deleted connection: {}", name);
    }

    public Map<String, ThresholdConfig> loadThresholds() {
        final File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        if (!file.exists()) {
            logger.info("No thresholds file found, returning empty map");
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(file)) {
            final Type mapType = new TypeToken<Map<String, ThresholdConfig>>() {
            }.getType();
            final Map<String, ThresholdConfig> thresholds = gson.fromJson(reader, mapType);
            logger.info("Loaded {} threshold(s)", thresholds != null ? thresholds.size() : 0);
            return thresholds != null ? thresholds : new HashMap<>();
        } catch (IOException e) {
            logger.error("Failed to load thresholds", e);
            return new HashMap<>();
        }
    }

    public void saveThresholds(Map<String, ThresholdConfig> thresholds) {
        final File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(thresholds, writer);
            logger.info("Saved {} threshold(s)", thresholds.size());
        } catch (IOException e) {
            logger.error("Failed to save thresholds", e);
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
        logger.info("load hierarchy: {}", file);
        if (!file.exists()) {
            logger.info("No hierarchy file found");
            return null;
        }

        try (Reader reader = new FileReader(file)) {
            final HierarchyConfig hierarchyConfig = gson.fromJson(reader, HierarchyConfig.class);
            logger.info("Loaded hierarchy with {} nodes", hierarchyConfig != null ? hierarchyConfig.getNodes().size() : 0);
            if (!this.connections.isEmpty() && hierarchyConfig != null && hierarchyConfig.getNodes() != null) {
                hierarchyConfig.getNodes().keySet().parallelStream().forEach(key -> {
                    final QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.fromFile(key,this.connections.get(0).getQueueManager());
                    hierarchyConfig.getNode(key).setQueueBrowserConfig(queueBrowserConfig);
                });
            }
            return hierarchyConfig;
        } catch (IOException e) {
            logger.error("Failed to load hierarchy", e);
            return null;
        }
    }

    /**
     * Save the queue manager hierarchy to JSON file.
     *
     * @param hierarchy The hierarchy configuration to save
     */
    public void saveHierarchy(HierarchyConfig hierarchy) {
        if (hierarchy == null) {
            logger.warn("Attempted to save null hierarchy");
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
                logger.warn("Failed to create backup of hierarchy file", e);
            }
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(hierarchy, writer);
            logger.info("Saved hierarchy with {} nodes", hierarchy.getNodes().size());
        } catch (IOException e) {
            logger.error("Failed to save hierarchy", e);
        }
    }

    /**
     * Create a default hierarchy from existing connections.
     * All connections are placed at root level (no folders).
     *
     * @param connections List of connection configurations
     * @return New HierarchyConfig with all connections at root
     */
    public HierarchyConfig createDefaultHierarchy(List<ConnectionConfig> connections) {
        final HierarchyConfig hierarchy = new HierarchyConfig();

        logger.info("Creating default hierarchy from {} connections", connections.size());

        for (ConnectionConfig config : connections) {
            final String displayName = config.getName() != null && !config.getName().isEmpty()
                    ? config.getName()
                    : config.getQueueManager() + "@" + config.getHost();

            final HierarchyNode node = new HierarchyNode(
                    HierarchyNode.NodeType.BROWSER,
                    displayName,
                    config.getName()
            );

            hierarchy.addNode(node, null);  // Add to root level
        }

        logger.info("Created default hierarchy with {} queue managers", connections.size());
        return hierarchy;
    }
}
