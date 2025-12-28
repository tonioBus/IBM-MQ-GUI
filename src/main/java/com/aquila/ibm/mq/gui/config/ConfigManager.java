package com.aquila.ibm.mq.gui.config;

import com.aquila.ibm.mq.gui.model.ConnectionConfig;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
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
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".ibmmqgui";
    private static final String CONNECTIONS_FILE = "connections.json";
    private static final String THRESHOLDS_FILE = "thresholds.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager() {
        initConfigDirectory();
    }

    private void initConfigDirectory() {
        try {
            Path configPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                logger.info("Created config directory: {}", CONFIG_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
    }

    public List<ConnectionConfig> loadConnections() {
        File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        if (!file.exists()) {
            logger.info("No connections file found, returning empty list");
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<List<ConnectionConfig>>(){}.getType();
            List<ConnectionConfig> connections = gson.fromJson(reader, listType);
            logger.info("Loaded {} connection(s)", connections != null ? connections.size() : 0);
            return connections != null ? connections : new ArrayList<>();
        } catch (IOException e) {
            logger.error("Failed to load connections", e);
            return new ArrayList<>();
        }
    }

    public void saveConnections(List<ConnectionConfig> connections) {
        File file = new File(CONFIG_DIR, CONNECTIONS_FILE);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(connections, writer);
            logger.info("Saved {} connection(s)", connections.size());
        } catch (IOException e) {
            logger.error("Failed to save connections", e);
        }
    }

    public void saveConnection(ConnectionConfig connection) {
        List<ConnectionConfig> connections = loadConnections();

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
        List<ConnectionConfig> connections = loadConnections();
        connections.removeIf(c -> c.getName().equals(name));
        saveConnections(connections);
        logger.info("Deleted connection: {}", name);
    }

    public Map<String, ThresholdConfig> loadThresholds() {
        File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        if (!file.exists()) {
            logger.info("No thresholds file found, returning empty map");
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(file)) {
            Type mapType = new TypeToken<Map<String, ThresholdConfig>>(){}.getType();
            Map<String, ThresholdConfig> thresholds = gson.fromJson(reader, mapType);
            logger.info("Loaded {} threshold(s)", thresholds != null ? thresholds.size() : 0);
            return thresholds != null ? thresholds : new HashMap<>();
        } catch (IOException e) {
            logger.error("Failed to load thresholds", e);
            return new HashMap<>();
        }
    }

    public void saveThresholds(Map<String, ThresholdConfig> thresholds) {
        File file = new File(CONFIG_DIR, THRESHOLDS_FILE);
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(thresholds, writer);
            logger.info("Saved {} threshold(s)", thresholds.size());
        } catch (IOException e) {
            logger.error("Failed to save thresholds", e);
        }
    }

    public void saveThreshold(String queueName, ThresholdConfig threshold) {
        Map<String, ThresholdConfig> thresholds = loadThresholds();
        thresholds.put(queueName, threshold);
        saveThresholds(thresholds);
    }

    public ThresholdConfig getThreshold(String queueName) {
        Map<String, ThresholdConfig> thresholds = loadThresholds();
        return thresholds.getOrDefault(queueName, new ThresholdConfig());
    }

    public String getConfigDirectory() {
        return CONFIG_DIR;
    }
}
