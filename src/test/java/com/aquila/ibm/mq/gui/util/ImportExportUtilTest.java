package com.aquila.ibm.mq.gui.util;

import com.aquila.ibm.mq.gui.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class ImportExportUtilTest {

    private ImportConfig testConfig;
    private Map<String, QueueManagerConfig> testQueueManagers;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Create test data
        testQueueManagers = new HashMap<>();

        QueueManagerConfig qm1 = new QueueManagerConfig();
        qm1.setName("QM1");
        qm1.setHost("192.168.1.73");
        qm1.setPort(1414);
        qm1.setChannel("DEV.APP.SVRCONN");
        qm1.setQueueManager("QM1");
        qm1.setUsername("admin");
        qm1.setPassword("passw0rd");
        qm1.setSslEnabled(false);

        QueueManagerConfig qm2 = new QueueManagerConfig();
        qm2.setName("QM2");
        qm2.setHost("192.168.1.73");
        qm2.setPort(1415);
        qm2.setChannel("DEV.APP.SVRCONN");
        qm2.setQueueManager("QM2");
        qm2.setUsername("admin");
        qm2.setPassword("passw0rd");
        qm2.setSslEnabled(false);

        testQueueManagers.put("192.168.1.73(1414)", qm1);
        testQueueManagers.put("192.168.1.73(1415)", qm2);

        testConfig = new ImportConfig();
        testConfig.setQueueManagers(testQueueManagers);

        // Create test hierarchy
        HierarchyImportNode hierarchy = new HierarchyImportNode("MERCURY", HierarchyNode.NodeType.FOLDER);
        HierarchyImportNode devNode = new HierarchyImportNode("DEV", HierarchyNode.NodeType.BROWSER);
        devNode.addChild("DEV.QUEUE.1", "D-10");
        devNode.addChild("DEV.QUEUE.2", "D-20");
        devNode.addChild("DEV.QUEUE.3", "D-23");
        hierarchy.addChild("DEV", devNode);

        testConfig.setHierarchy(hierarchy);
    }

    @AfterEach
    void tearDown() {
        testConfig = null;
        testQueueManagers = null;
    }

    @Test
    void testImportFromFile() {
        // Test importing from the actual doc/import.json file
        ImportConfig config = ImportExportUtil.importFromFile("doc/import.json");

        assertNotNull(config, "ImportConfig should not be null");
        assertNotNull(config.getQueueManagers(), "Queue managers map should not be null");
        assertFalse(config.getQueueManagers().isEmpty(), "Queue managers should not be empty");

        log.info("Imported {} queue managers", config.getQueueManagers().size());

        // Verify queue manager structure
        for (Map.Entry<String, QueueManagerConfig> entry : config.getQueueManagers().entrySet()) {
            QueueManagerConfig qm = entry.getValue();
            assertNotNull(qm.getName(), "Queue manager name should not be null");
            assertNotNull(qm.getHost(), "Queue manager host should not be null");
            assertTrue(qm.getPort() > 0, "Queue manager port should be positive");
            log.info("Queue Manager: {} -> {}:{}", entry.getKey(), qm.getHost(), qm.getPort());
        }

        // Verify hierarchy if it exists
        if (config.getHierarchy() != null) {
            assertNotNull(config.getHierarchy().getName(), "Hierarchy root name should not be null");
            assertNotNull(config.getHierarchy().getType(), "Hierarchy root type should not be null");
            log.info("Hierarchy root: {} (type: {})",
                config.getHierarchy().getName(),
                config.getHierarchy().getType());
        }
    }

    @Test
    void testExportToFile() {
        // Create a temporary file for export
        File exportFile = new File(tempDir.toFile(), "test-export.json");

        // Create a simple hierarchy for testing
        HierarchyConfig hierarchyConfig = new HierarchyConfig();
        HierarchyNode rootNode = new HierarchyNode(HierarchyNode.NodeType.FOLDER, "TEST_ROOT");
        hierarchyConfig.addNode(rootNode, null);

        // Export
        boolean success = ImportExportUtil.exportToFile(
            exportFile.getAbsolutePath(),
            hierarchyConfig,
            testQueueManagers
        );

        assertTrue(success, "Export should succeed");
        assertTrue(exportFile.exists(), "Export file should exist");
        assertTrue(exportFile.length() > 0, "Export file should not be empty");

        log.info("Exported to: {} (size: {} bytes)", exportFile.getAbsolutePath(), exportFile.length());
    }

    @Test
    void testImportHierarchy() {
        // Import hierarchy from doc/import.json
        HierarchyConfig hierarchyConfig = ImportExportUtil.importHierarchy("doc/import.json");

        assertNotNull(hierarchyConfig, "HierarchyConfig should not be null");
        assertNotNull(hierarchyConfig.getNodes(), "Nodes map should not be null");
        assertFalse(hierarchyConfig.getRootNodeIds().isEmpty(), "Should have at least one root node");

        log.info("Imported hierarchy with {} nodes and {} root nodes",
            hierarchyConfig.getNodes().size(),
            hierarchyConfig.getRootNodeIds().size());

        // Verify root node
        String firstRootId = hierarchyConfig.getRootNodeIds().get(0);
        HierarchyNode rootNode = hierarchyConfig.getNode(firstRootId);
        assertNotNull(rootNode, "Root node should not be null");
        assertNotNull(rootNode.getName(), "Root node name should not be null");

        log.info("Root node: {} (type: {})", rootNode.getName(), rootNode.getType());
    }

    @Test
    void testRoundTripConversion() {
        // Test: ImportConfig -> HierarchyConfig -> ImportConfig

        // Step 1: Convert to HierarchyConfig
        HierarchyConfig hierarchyConfig = testConfig.toHierarchyConfig();
        assertNotNull(hierarchyConfig, "HierarchyConfig should not be null");
        assertFalse(hierarchyConfig.getNodes().isEmpty(), "Should have nodes");

        log.info("Step 1: Converted to HierarchyConfig with {} nodes",
            hierarchyConfig.getNodes().size());

        // Step 2: Convert back to ImportConfig
        ImportConfig reconvertedConfig = ImportConfig.fromHierarchyConfig(
            hierarchyConfig,
            testQueueManagers
        );

        assertNotNull(reconvertedConfig, "Reconverted config should not be null");
        assertNotNull(reconvertedConfig.getQueueManagers(), "Queue managers should not be null");
        assertEquals(testQueueManagers.size(), reconvertedConfig.getQueueManagers().size(),
            "Should have same number of queue managers");

        log.info("Step 2: Reconverted back to ImportConfig");

        // Verify queue managers preserved
        for (String key : testQueueManagers.keySet()) {
            assertTrue(reconvertedConfig.getQueueManagers().containsKey(key),
                "Should contain queue manager: " + key);
        }
    }

    @Test
    void testWriteAndReadBack() {
        // Write test config to file
        File testFile = new File(tempDir.toFile(), "test-config.json");
        boolean writeSuccess = testConfig.toFile(testFile);

        assertTrue(writeSuccess, "Writing should succeed");
        assertTrue(testFile.exists(), "File should exist");

        log.info("Wrote config to: {}", testFile.getAbsolutePath());

        // Read it back
        ImportConfig readConfig = ImportConfig.fromFile(testFile);

        assertNotNull(readConfig, "Read config should not be null");
        assertNotNull(readConfig.getQueueManagers(), "Queue managers should not be null");
        assertEquals(testQueueManagers.size(), readConfig.getQueueManagers().size(),
            "Should have same number of queue managers");

        // Verify content
        for (Map.Entry<String, QueueManagerConfig> entry : testQueueManagers.entrySet()) {
            String key = entry.getKey();
            QueueManagerConfig original = entry.getValue();
            QueueManagerConfig read = readConfig.getQueueManagers().get(key);

            assertNotNull(read, "Queue manager should exist: " + key);
            assertEquals(original.getName(), read.getName(), "Name should match");
            assertEquals(original.getHost(), read.getHost(), "Host should match");
            assertEquals(original.getPort(), read.getPort(), "Port should match");
            assertEquals(original.getChannel(), read.getChannel(), "Channel should match");
            assertEquals(original.getQueueManager(), read.getQueueManager(), "QueueManager should match");
        }

        log.info("Read back config successfully with {} queue managers",
            readConfig.getQueueManagers().size());
    }

    @Test
    void testImportNonExistentFile() {
        ImportConfig config = ImportConfig.fromFile("nonexistent-file.json");
        assertNull(config, "Should return null for non-existent file");
    }

    @Test
    void testExportWithNullHierarchy() {
        File exportFile = new File(tempDir.toFile(), "null-hierarchy-export.json");

        boolean success = ImportExportUtil.exportToFile(
            exportFile.getAbsolutePath(),
            null,
            testQueueManagers
        );

        assertTrue(success, "Should succeed even with null hierarchy");
        assertTrue(exportFile.exists(), "File should be created");

        // Read it back to verify
        ImportConfig readConfig = ImportConfig.fromFile(exportFile);
        assertNotNull(readConfig, "Should read back successfully");
        assertEquals(testQueueManagers.size(), readConfig.getQueueManagers().size(),
            "Should preserve queue managers");
    }

    @Test
    void testConversionWithComplexHierarchy() {
        // Create a more complex hierarchy
        HierarchyImportNode root = new HierarchyImportNode("ROOT", HierarchyNode.NodeType.FOLDER);

        HierarchyImportNode folder1 = new HierarchyImportNode("Folder1", HierarchyNode.NodeType.FOLDER);
        HierarchyImportNode browser1 = new HierarchyImportNode("Browser1", HierarchyNode.NodeType.BROWSER);
        browser1.addChild("QUEUE.1", "Q1");
        browser1.addChild("QUEUE.2", "Q2");

        folder1.addChild("Browser1", browser1);
        root.addChild("Folder1", folder1);

        HierarchyImportNode browser2 = new HierarchyImportNode("Browser2", HierarchyNode.NodeType.BROWSER);
        browser2.addChild("QUEUE.3", "Q3");
        root.addChild("Browser2", browser2);

        testConfig.setHierarchy(root);

        // Convert to HierarchyConfig
        HierarchyConfig hierarchyConfig = testConfig.toHierarchyConfig();

        assertNotNull(hierarchyConfig);
        assertTrue(hierarchyConfig.getNodes().size() > 1, "Should have multiple nodes");
        assertFalse(hierarchyConfig.getRootNodeIds().isEmpty(), "Should have root nodes");

        log.info("Complex hierarchy converted with {} nodes", hierarchyConfig.getNodes().size());

        // Verify we can navigate the hierarchy
        String rootId = hierarchyConfig.getRootNodeIds().get(0);
        HierarchyNode rootNode = hierarchyConfig.getNode(rootId);
        assertNotNull(rootNode);
        assertTrue(rootNode.isFolder(), "Root should be a folder");
        assertFalse(rootNode.getChildIds().isEmpty(), "Root should have children");

        log.info("Root node '{}' has {} children", rootNode.getName(), rootNode.getChildIds().size());
    }

    /**
     * Example usage demonstrating how to read an import file.
     */
    @Test
    public void testRead() {
        // Read from file
        ImportConfig config = ImportConfig.fromFile("doc/import.json");

        if (config != null) {
            // Access queue managers
            Map<String, QueueManagerConfig> queueManagers = config.getQueueManagers();
            log.info("Loaded {} queue managers", queueManagers.size());

            for (Map.Entry<String, QueueManagerConfig> entry : queueManagers.entrySet()) {
                String key = entry.getKey();
                QueueManagerConfig qm = entry.getValue();
                log.info("Queue Manager: {} -> {}:{}",
                        key, qm.getHost(), qm.getPort());
            }

            // Convert to internal hierarchy format
            HierarchyConfig hierarchyConfig = config.toHierarchyConfig();
            log.info("Converted to hierarchy with {} nodes",
                    hierarchyConfig.getNodes().size());
        }
    }

    /**
     * Example usage demonstrating how to write an export file.
     */
    public void exampleWrite(HierarchyConfig hierarchyConfig,
                                    Map<String, QueueManagerConfig> queueManagers) {
        // Create import config from internal format
        ImportConfig importConfig = ImportConfig.fromHierarchyConfig(hierarchyConfig, queueManagers);

        // Write to file
        boolean success = importConfig.toFile("export.json");

        if (success) {
            log.info("Successfully exported configuration");
        }
    }

}
