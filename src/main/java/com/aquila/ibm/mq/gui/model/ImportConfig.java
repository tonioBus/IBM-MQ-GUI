package com.aquila.ibm.mq.gui.model;

import com.aquila.ibm.mq.gui.model.node.HierarchyNode;
import com.aquila.ibm.mq.gui.model.node.QueueNode;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
                final Map<String, QueueNode.QueueDescription> descriptions = new HashMap<>();
                importNode.getChildren().entrySet().stream()
                        .map(entry -> descriptions.put((String) entry.getValue(), new QueueNode.QueueDescription(entry.getKey())));
                final QueueNode queueNode = QueueNode.builder()
                        .descriptions(descriptions)
                        .queuePattern("*")
                        .queueManager(importNode.getQueueMgr())
                        .build();
                node.setQueueNode(queueNode);
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
     * Create an ImportConfig from a selected node in the hierarchy (subtree export).
     * Only includes queue managers that are referenced by the exported hierarchy.
     *
     * @param selectedNode      The node to export (and all its children)
     * @param hierarchyConfig   The full hierarchy configuration
     * @param allQueueManagers  All available queue manager configurations
     * @return ImportConfig object containing only the selected subtree and referenced queue managers
     */
    public static ImportConfig fromSelectedNode(HierarchyNode selectedNode,
                                                 HierarchyConfig hierarchyConfig,
                                                 Map<String, QueueManagerConfig> allQueueManagers) {
        final ImportConfig importConfig = new ImportConfig();

        if (selectedNode == null) {
            return importConfig;
        }

        // Collect all queue manager references used in the subtree
        final Set<String> usedQueueManagers = new HashSet<>();
        collectQueueManagerReferences(selectedNode, hierarchyConfig, usedQueueManagers);

        // Filter queue managers to only include those referenced in the subtree
        final Map<String, QueueManagerConfig> filteredQueueManagers = new HashMap<>();
        if (allQueueManagers != null) {
            for (String qmKey : usedQueueManagers) {
                if (allQueueManagers.containsKey(qmKey)) {
                    filteredQueueManagers.put(qmKey, allQueueManagers.get(qmKey));
                }
            }
        }
        importConfig.setQueueManagers(filteredQueueManagers);

        // Build hierarchy map with the selected node as root
        final Map<String, Object> hierarchyMap = new HashMap<>();
        HierarchyImportNode importNode = convertToImportNode(selectedNode, hierarchyConfig);
        hierarchyMap.put(selectedNode.getName(), importNode.toMap());
        importConfig.setHierarchy(hierarchyMap);

        return importConfig;
    }

    /**
     * Recursively collect all queue manager references from a node and its children.
     */
    private static void collectQueueManagerReferences(HierarchyNode node,
                                                      HierarchyConfig hierarchyConfig,
                                                      Set<String> queueManagerRefs) {
        if (node == null) {
            return;
        }

        // If this is a queue node, add its queue manager reference
        if (node.isQueue() && node.getQueueNode() != null) {
            String qmRef = node.getQueueNode().getQueueManager();
            if (qmRef != null && !qmRef.isEmpty()) {
                queueManagerRefs.add(qmRef);
            }
        }

        // Recursively process children
        for (String childId : node.getChildIds()) {
            HierarchyNode childNode = hierarchyConfig.getNode(childId);
            if (childNode != null) {
                collectQueueManagerReferences(childNode, hierarchyConfig, queueManagerRefs);
            }
        }
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
        if (node.getType() == HierarchyNode.NodeType.QUEUE && node.getQueueNode() != null) {
            final QueueNode config = node.getQueueNode();
            importNode.setQueueMgr(config.getQueueManager());

            // Export descriptions as children (queueName -> identifier)
            if (config.getDescriptions() != null) {
                for (Map.Entry<String, QueueNode.QueueDescription> entry : config.getDescriptions().entrySet()) {
                    final String identifier = entry.getKey();
                    final QueueNode.QueueDescription desc = entry.getValue();
                    children.put(desc.label(), identifier);
                }
            }
        } else if (node.isFolder()) {
            // Process child nodes for FOLDER type
            for (String childId : node.getChildIds()) {
                final HierarchyNode childNode = hierarchyConfig.getNode(childId);
                if (childNode != null) {
                    // Always recursively convert child nodes (both folders and queues)
                    children.put(childNode.getName(), convertToImportNode(childNode, hierarchyConfig).toMap());
                }
            }
        }

        if (!children.isEmpty()) {
            importNode.setChildren(children);
        }

        return importNode;
    }
}