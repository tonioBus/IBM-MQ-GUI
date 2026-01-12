package com.aquila.ibm.mq.gui.util;

import com.aquila.ibm.mq.gui.model.HierarchyConfig;
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

}
