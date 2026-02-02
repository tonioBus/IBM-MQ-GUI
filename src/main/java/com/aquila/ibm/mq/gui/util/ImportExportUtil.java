/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Utility class for importing and exporting IBM MQ GUI configurations.
 * Provides convenient methods for reading/writing JSON configuration files
 * with support for full and selective (subtree) exports.
 */
package com.aquila.ibm.mq.gui.util;

import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.model.node.HierarchyNode;
import com.aquila.ibm.mq.gui.model.ImportConfig;
import com.aquila.ibm.mq.gui.model.QueueManagerConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Utility class for importing and exporting IBM MQ GUI configurations.
 * Provides convenient methods for reading/writing import.json files.
 */
@Slf4j
public class ImportExportUtil {

    /**
     * Import configuration from a file.
     *
     * @param filePath Path to the import JSON file
     * @return ImportConfig object, or null if import fails
     */
    public static ImportConfig importFromFile(String filePath) {
        log.info("Importing configuration from: {}", filePath);
        ImportConfig config = ImportConfig.fromFile(filePath);

        if (config != null) {
            log.info("Successfully imported {} queue managers",
                config.getQueueManagers() != null ? config.getQueueManagers().size() : 0);
        }

        return config;
    }

    /**
     * Export configuration to a file.
     *
     * @param filePath Path where to save the export JSON file
     * @param hierarchyConfig The hierarchy configuration to export
     * @param queueManagers The queue manager configurations to export
     * @return true if export succeeds, false otherwise
     */
    public static boolean exportToFile(String filePath,
                                      HierarchyConfig hierarchyConfig,
                                      Map<String, QueueManagerConfig> queueManagers) {
        log.info("Exporting configuration to: {}", filePath);

        ImportConfig importConfig = ImportConfig.fromHierarchyConfig(hierarchyConfig, queueManagers);

        boolean success = importConfig.toFile(filePath);

        if (success) {
            log.info("Successfully exported {} queue managers",
                queueManagers != null ? queueManagers.size() : 0);
        }

        return success;
    }

    /**
     * Import and convert configuration to internal format.
     *
     * @param filePath Path to the import JSON file
     * @return HierarchyConfig object, or null if import fails
     */
    public static HierarchyConfig importHierarchy(String filePath) {
        ImportConfig importConfig = importFromFile(filePath);

        if (importConfig == null) {
            return null;
        }

        return importConfig.toHierarchyConfig();
    }

    /**
     * Export a selected node (subtree) to a file.
     * Only includes queue managers that are referenced by the exported hierarchy.
     *
     * @param filePath          Path where to save the export JSON file
     * @param selectedNode      The node to export (and all its children)
     * @param hierarchyConfig   The full hierarchy configuration
     * @param queueManagers     All available queue manager configurations
     * @return true if export succeeds, false otherwise
     */
    public static boolean exportSelectedToFile(String filePath,
                                                HierarchyNode selectedNode,
                                                HierarchyConfig hierarchyConfig,
                                                Map<String, QueueManagerConfig> queueManagers) {
        log.info("Exporting selected node '{}' to: {}", selectedNode.getName(), filePath);

        ImportConfig importConfig = ImportConfig.fromSelectedNode(selectedNode, hierarchyConfig, queueManagers);

        boolean success = importConfig.toFile(filePath);

        if (success) {
            log.info("Successfully exported selected node '{}' with {} queue manager(s)",
                selectedNode.getName(),
                importConfig.getQueueManagers() != null ? importConfig.getQueueManagers().size() : 0);
        }

        return success;
    }

}
