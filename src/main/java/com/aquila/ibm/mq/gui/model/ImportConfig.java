package com.aquila.ibm.mq.gui.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private Map<String, QueueManagerConfig> QueueManagers;
    private HierarchyImportNode Hierarchy;

    public ImportConfig() {
        this.QueueManagers = new HashMap<>();
    }

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
            ImportConfig config = gson.fromJson(reader, ImportConfig.class);
            log.info("Loaded import config with {} queue managers",
                config.QueueManagers != null ? config.QueueManagers.size() : 0);
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
     * Convert this ImportConfig to the application's internal HierarchyConfig format.
     *
     * @return HierarchyConfig object
     */
    public HierarchyConfig toHierarchyConfig() {
        HierarchyConfig hierarchyConfig = new HierarchyConfig();

        if (Hierarchy != null) {
            convertNode(Hierarchy, hierarchyConfig, null);
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
        HierarchyNode node = new HierarchyNode(importNode.getType(), importNode.getName());
        hierarchyConfig.addNode(node, parentId);

        // Process children
        if (importNode.getChildren() != null) {
            for (Map.Entry<String, Object> entry : importNode.getChildren().entrySet()) {
                String childName = entry.getKey();
                Object childValue = entry.getValue();

                if (childValue instanceof Map) {
                    // It's a nested node (folder or browser with properties)
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childMap = (Map<String, Object>) childValue;
                    HierarchyImportNode childNode = HierarchyImportNode.fromMap(childName, childMap);
                    convertNode(childNode, hierarchyConfig, node.getId());
                } else if (childValue instanceof String) {
                    // It's a leaf node (queue name -> identifier mapping for BROWSER nodes)
                    HierarchyNode leafNode = new HierarchyNode(HierarchyNode.NodeType.BROWSER, childName);
                    hierarchyConfig.addNode(leafNode, node.getId());
                }
            }
        }
    }

    /**
     * Create an ImportConfig from the application's internal HierarchyConfig format.
     *
     * @param hierarchyConfig The hierarchy configuration
     * @param queueManagers The queue manager configurations
     * @return ImportConfig object
     */
    public static ImportConfig fromHierarchyConfig(HierarchyConfig hierarchyConfig,
                                                   Map<String, QueueManagerConfig> queueManagers) {
        ImportConfig importConfig = new ImportConfig();
        importConfig.setQueueManagers(queueManagers != null ? new HashMap<>(queueManagers) : new HashMap<>());

        if (hierarchyConfig != null && !hierarchyConfig.getRootNodeIds().isEmpty()) {
            // Assuming single root for simplicity - take the first root node
            String firstRootId = hierarchyConfig.getRootNodeIds().get(0);
            HierarchyNode rootNode = hierarchyConfig.getNode(firstRootId);

            if (rootNode != null) {
                importConfig.setHierarchy(convertToImportNode(rootNode, hierarchyConfig));
            }
        }

        return importConfig;
    }

    /**
     * Recursively convert HierarchyNode to HierarchyImportNode.
     */
    private static HierarchyImportNode convertToImportNode(HierarchyNode node, HierarchyConfig hierarchyConfig) {
        HierarchyImportNode importNode = new HierarchyImportNode();
        importNode.setName(node.getName());
        importNode.setType(node.getType());

        Map<String, Object> children = new HashMap<>();

        // Process each child
        for (String childId : node.getChildIds()) {
            HierarchyNode childNode = hierarchyConfig.getNode(childId);
            if (childNode != null) {
                if (childNode.isFolder() && !childNode.getChildIds().isEmpty()) {
                    // Nested folder
                    children.put(childNode.getName(), convertToImportNode(childNode, hierarchyConfig).toMap());
                } else if (childNode.isQueueBrowser()) {
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

        if (!children.isEmpty()) {
            importNode.setChildren(children);
        }

        return importNode;
    }
}