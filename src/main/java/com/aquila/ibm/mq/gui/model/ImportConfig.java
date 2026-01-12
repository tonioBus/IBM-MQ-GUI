package com.aquila.ibm.mq.gui.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * POJO for importing/exporting IBM MQ GUI configuration.
 * Matches the structure of doc/import.json file.
 */
@Getter
@Setter
@ToString
@Slf4j
public class ImportConfig {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Map<String, QueueManagerConfig> queueManagers = new HashMap<>();
    private Map<String, Object> hierarchy;

    /**
     * Read ImportConfig from a JSON file.
     *
     * @param file The file to read from
     * @return ImportConfig object, or null if reading fails
     */
    public static ImportConfig fromFile(File file) {
        if (!file.exists()) {
            log.error("Import file does not exist: {}", file.getAbsolutePath());
            return null;
        }

        try (Reader reader = new FileReader(file)) {
            final ImportConfig config = gson.fromJson(reader, ImportConfig.class);
            log.info("Loaded import config with {} queue managers",
                    config.queueManagers != null ? config.queueManagers.size() : 0);
            return config;
        } catch (IOException e) {
            log.error("Failed to read import file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Read ImportConfig from a JSON file path.
     *
     * @param filePath The path to the file
     * @return ImportConfig object, or null if reading fails
     */
    public static ImportConfig fromFile(String filePath) {
        return fromFile(new File(filePath));
    }

    /**
     * Write this ImportConfig to a JSON file.
     *
     * @param file The file to write to
     * @return true if successful, false otherwise
     */
    public boolean toFile(File file) {
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(this, writer);
            log.info("Saved import config to: {}", file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("Failed to write import file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Write this ImportConfig to a JSON file path.
     *
     * @param filePath The path to write to
     * @return true if successful, false otherwise
     */
    public boolean toFile(String filePath) {
        return toFile(new File(filePath));
    }

    /**
     * Read ImportConfig from a JSON file using Jackson.
     *
     * @param file The file to read from
     * @return ImportConfig object, or null if reading fails
     */
    public static ImportConfig fromFileJackson(File file) {
        if (!file.exists()) {
            log.error("Import file does not exist: {}", file.getAbsolutePath());
            return null;
        }

        try {
            final ImportConfig config = objectMapper.readValue(file, ImportConfig.class);
            log.info("Loaded import config with {} queue managers (Jackson)",
                    config.queueManagers != null ? config.queueManagers.size() : 0);
            return config;
        } catch (IOException e) {
            log.error("Failed to read import file with Jackson: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Convert this ImportConfig to the application's internal HierarchyConfig format.
     *
     * @return HierarchyConfig object
     */
    public HierarchyConfig toHierarchyConfig() {
        final HierarchyConfig hierarchyConfig = new HierarchyConfig();

        if (hierarchy != null && !hierarchy.isEmpty()) {
            // Iterate over each root node in the hierarchy map
            for (Map.Entry<String, Object> entry : hierarchy.entrySet()) {
                final String nodeName = entry.getKey();
                final Object nodeValue = entry.getValue();

                if (nodeValue instanceof Map) {
                    @SuppressWarnings("unchecked") final Map<String, Object> nodeMap = (Map<String, Object>) nodeValue;
                    final HierarchyImportNode importNode = HierarchyImportNode.fromMap(nodeName, nodeMap);
                    if (importNode != null) {
                        convertNode(importNode, hierarchyConfig, null);
                    }
                }
            }
        }

        return hierarchyConfig;
    }

    /**
     * Recursively convert HierarchyImportNode to HierarchyNode and add to HierarchyConfig.
     */
    private void convertNode(HierarchyImportNode importNode, HierarchyConfig hierarchyConfig, String parentId) {
        if (importNode == null) {
            return;
        }

        // Create the current node
        final HierarchyNode node = new HierarchyNode(importNode.getType(), importNode.getName());
        hierarchyConfig.addNode(node, parentId);

        // Process children
        if (importNode.getChildren() != null) {
            if (importNode.getType() == HierarchyNode.NodeType.QUEUE) {
                final Map<String, QueueBrowserConfig.QueueDescription> descriptions = new HashMap<>();
                importNode.getChildren().entrySet().stream()
                        .map(entry -> descriptions.put((String) entry.getValue(), new QueueBrowserConfig.QueueDescription(entry.getKey())));
                final QueueBrowserConfig queueBrowserConfig = QueueBrowserConfig.builder()
                        .descriptions(descriptions)
                        .label(importNode.getName())
                        .regularExpression("*")
                        .queueManager(importNode.getQueueMgr())
                        .build();
                node.setQueueBrowserConfig(queueBrowserConfig);
            } else
                for (Map.Entry<String, Object> entry : importNode.getChildren().entrySet()) {
                    final String childName = entry.getKey();
                    final Object childValue = entry.getValue();

                    if (childValue instanceof Map) {
                        // It's a nested node (folder or browser with properties)
                        @SuppressWarnings("unchecked") final Map<String, Object> childMap = (Map<String, Object>) childValue;
                        final HierarchyImportNode childNode = HierarchyImportNode.fromMap(childName, childMap);
                        convertNode(childNode, hierarchyConfig, node.getId());
                    } else if (childValue instanceof String) {
                        // It's a leaf node (queue name -> identifier mapping for BROWSER nodes)
                        final HierarchyNode leafNode = new HierarchyNode(HierarchyNode.NodeType.QUEUE, childName);
                        hierarchyConfig.addNode(leafNode, node.getId());
                    }
                }
        }
    }

    /**
     * Create an ImportConfig from the application's internal HierarchyConfig format.
     *
     * @param hierarchyConfig The hierarchy configuration
     * @param queueManagers   The queue manager configurations
     * @return ImportConfig object
     */
    public static ImportConfig fromHierarchyConfig(HierarchyConfig hierarchyConfig,
                                                   Map<String, QueueManagerConfig> queueManagers) {
        final ImportConfig importConfig = new ImportConfig();
        importConfig.setQueueManagers(queueManagers != null ? new HashMap<>(queueManagers) : new HashMap<>());

        if (hierarchyConfig != null && !hierarchyConfig.getRootNodeIds().isEmpty()) {
            final Map<String, Object> hierarchyMap = new HashMap<>();

            // Process all root nodes
            for (String rootId : hierarchyConfig.getRootNodeIds()) {
                final HierarchyNode rootNode = hierarchyConfig.getNode(rootId);
                if (rootNode != null) {
                    HierarchyImportNode importNode = convertToImportNode(rootNode, hierarchyConfig);
                    hierarchyMap.put(rootNode.getName(), importNode.toMap());
                }
            }

            importConfig.setHierarchy(hierarchyMap);
        }

        return importConfig;
    }

    /**
     * Recursively convert HierarchyNode to HierarchyImportNode.
     */
    private static HierarchyImportNode convertToImportNode(HierarchyNode node, HierarchyConfig hierarchyConfig) {
        final HierarchyImportNode importNode = new HierarchyImportNode();
        importNode.setName(node.getName());
        importNode.setType(node.getType());

        final Map<String, Object> children = new HashMap<>();

        // For QUEUE type nodes, export the queue browser config
        if (node.getType() == HierarchyNode.NodeType.QUEUE && node.getQueueBrowserConfig() != null) {
            final QueueBrowserConfig config = node.getQueueBrowserConfig();
            importNode.setQueueMgr(config.getQueueManager());

            // Export descriptions as children (queueName -> identifier)
            if (config.getDescriptions() != null) {
                for (Map.Entry<String, QueueBrowserConfig.QueueDescription> entry : config.getDescriptions().entrySet()) {
                    final String identifier = entry.getKey();
                    final QueueBrowserConfig.QueueDescription desc = entry.getValue();
                    children.put(desc.label(), identifier);
                }
            }
        } else {
            // Process regular child nodes for FOLDER and BROWSER types
            for (String childId : node.getChildIds()) {
                final HierarchyNode childNode = hierarchyConfig.getNode(childId);
                if (childNode != null) {
                    if (childNode.isFolder() && !childNode.getChildIds().isEmpty()) {
                        // Nested folder
                        children.put(childNode.getName(), convertToImportNode(childNode, hierarchyConfig).toMap());
                    } else if (childNode.isQueue()) {
                        // Queue browser - could be simplified to just name or include full structure
                        if (!childNode.getChildIds().isEmpty()) {
                            children.put(childNode.getName(), convertToImportNode(childNode, hierarchyConfig).toMap());
                        } else {
                            // Leaf browser node - use simple string identifier
                            children.put(childNode.getName(), childNode.getId());
                        }
                    }
                }
            }
        }

        if (!children.isEmpty()) {
            importNode.setChildren(children);
        }

        return importNode;
    }
}