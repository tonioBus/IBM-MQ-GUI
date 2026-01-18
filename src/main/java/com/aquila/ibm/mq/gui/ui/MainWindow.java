package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.importation.*;
import com.aquila.ibm.mq.gui.model.*;
import com.aquila.ibm.mq.gui.mq.MQConnectionManager;
import com.aquila.ibm.mq.gui.mq.MessageService;
import com.aquila.ibm.mq.gui.mq.QueueMonitor;
import com.aquila.ibm.mq.gui.mq.QueueService;
import com.aquila.ibm.mq.gui.util.ImportExportUtil;
import com.ibm.mq.MQException;
import com.ibm.mq.headers.MQDataException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class MainWindow {

    private final Display display;
    @Getter
    private final Shell shell;
    private final ConfigManager configManager;
    private final MQConnectionManager connectionManager;
    private final QueueService queueService;
    private final MessageService messageService;
    private final AlertManager alertManager;
    private QueueMonitor queueMonitor;

    private HierarchyTreeViewer hierarchyTreeViewer;
    private QueueListViewer queueListViewer;
    private TabFolder tabFolder;
    private QueuePropertiesPanel propertiesPanel;
    private MessageBrowserPanel messageBrowserPanel;
    private SendMessageDialog sendMessageDialog;
    private DepthChartPanel depthChartPanel;
    private QueueHandlesPanel queueHandlesPanel;
    private Label statusLabel;
    private Label alertLabel;

    private QueueInfo selectedQueue;

    public MainWindow(Display display) {
        this.display = display;
        this.configManager = new ConfigManager();
        this.connectionManager = new MQConnectionManager();
        this.queueService = new QueueService(connectionManager);
        this.messageService = new MessageService(connectionManager);
        this.alertManager = new AlertManager(configManager);

        shell = new Shell(display);
        shell.setText("IBM MQ Queue Manager GUI");
        shell.setSize(1600, 800);
        shell.setLayout(new GridLayout());

        createMenuBar();
        createMainContent();
        createStatusBar();

        shell.addDisposeListener(e -> cleanup());

        // Load hierarchy on startup
        loadHierarchy();
    }

    private void createMenuBar() {
        Menu menuBar = new Menu(shell, SWT.BAR);
        shell.setMenuBar(menuBar);

        createFileMenu(menuBar);
        createConnectionMenu(menuBar);
        createViewMenu(menuBar);
        createToolsMenu(menuBar);
        createHelpMenu(menuBar);
    }

    private void createFileMenu(Menu menuBar) {
        MenuItem fileItem = new MenuItem(menuBar, SWT.CASCADE);
        fileItem.setText("&File");
        Menu fileMenu = new Menu(shell, SWT.DROP_DOWN);
        fileItem.setMenu(fileMenu);

        MenuItem importItem = new MenuItem(fileMenu, SWT.PUSH);
        importItem.setText("&Import Configuration...");
        importItem.addListener(SWT.Selection, e -> importConfiguration());

        MenuItem exportItem = new MenuItem(fileMenu, SWT.PUSH);
        exportItem.setText("&Export Configuration...");
        exportItem.addListener(SWT.Selection, e -> exportConfiguration());

        new MenuItem(fileMenu, SWT.SEPARATOR);

        MenuItem exitItem = new MenuItem(fileMenu, SWT.PUSH);
        exitItem.setText("E&xit");
        exitItem.addListener(SWT.Selection, e -> shell.close());
    }

    private void createConnectionMenu(Menu menuBar) {
        MenuItem connItem = new MenuItem(menuBar, SWT.CASCADE);
        connItem.setText("&Connection");
        Menu connMenu = new Menu(shell, SWT.DROP_DOWN);
        connItem.setMenu(connMenu);

        MenuItem connectItem = new MenuItem(connMenu, SWT.PUSH);
        connectItem.setText("&Connect...");
        connectItem.addListener(SWT.Selection, e -> showConnectionDialog());

        MenuItem disconnectItem = new MenuItem(connMenu, SWT.PUSH);
        disconnectItem.setText("&Disconnect");
        disconnectItem.addListener(SWT.Selection, e -> disconnect());
    }

    private void createViewMenu(Menu menuBar) {
        MenuItem viewItem = new MenuItem(menuBar, SWT.CASCADE);
        viewItem.setText("&View");
        Menu viewMenu = new Menu(shell, SWT.DROP_DOWN);
        viewItem.setMenu(viewMenu);

        MenuItem refreshItem = new MenuItem(viewMenu, SWT.PUSH);
        refreshItem.setText("&Refresh\tF5");
        refreshItem.setAccelerator(SWT.F5);
        refreshItem.addListener(SWT.Selection, e -> refreshQueues());

        MenuItem autoRefreshItem = new MenuItem(viewMenu, SWT.CHECK);
        autoRefreshItem.setText("&Auto-refresh");
        autoRefreshItem.addListener(SWT.Selection, e -> toggleAutoRefresh(autoRefreshItem.getSelection()));
    }

    private void createToolsMenu(Menu menuBar) {
        MenuItem toolsItem = new MenuItem(menuBar, SWT.CASCADE);
        toolsItem.setText("&Tools");
        Menu toolsMenu = new Menu(shell, SWT.DROP_DOWN);
        toolsItem.setMenu(toolsMenu);

        MenuItem thresholdsItem = new MenuItem(toolsMenu, SWT.PUSH);
        thresholdsItem.setText("Configure &Thresholds...");
        thresholdsItem.addListener(SWT.Selection, e -> showThresholdDialog());

        MenuItem clearAlertsItem = new MenuItem(toolsMenu, SWT.PUSH);
        clearAlertsItem.setText("Clear &Alerts");
        clearAlertsItem.addListener(SWT.Selection, e -> clearAlerts());

        MenuItem sendMessageItem = new MenuItem(toolsMenu, SWT.PUSH);
        sendMessageItem.setText("Send Message...");
        sendMessageItem.addListener(SWT.Selection, e -> showSendMessageDialog());

    }

    private void createHelpMenu(Menu menuBar) {
        MenuItem helpItem = new MenuItem(menuBar, SWT.CASCADE);
        helpItem.setText("&Help");
        Menu helpMenu = new Menu(shell, SWT.DROP_DOWN);
        helpItem.setMenu(helpMenu);

        MenuItem aboutItem = new MenuItem(helpMenu, SWT.PUSH);
        aboutItem.setText("&About");
        aboutItem.addListener(SWT.Selection, e -> showAbout());
    }

    private void createMainContent() {
        SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // NEW: Queue Manager Tree (20%)
        hierarchyTreeViewer = new HierarchyTreeViewer(
                sashForm, SWT.BORDER, connectionManager, configManager);
        hierarchyTreeViewer.addSelectionListener(this::onTreeSelection);

        // EXISTING: Queue List (30%)
        queueListViewer = new QueueListViewer(sashForm, SWT.BORDER, alertManager);
        queueListViewer.addSelectionListener(this::onQueueSelected);
        queueListViewer.setContextMenuActionListener(new QueueListViewer.ContextMenuActionListener() {
            @Override
            public void onSendMessage(QueueInfo queue) {
                handleSendMessage(queue);
            }

            @Override
            public void onBrowseMessages(QueueInfo queue) {
                handleBrowseMessages(queue);
            }

            @Override
            public void onRefreshQueue(QueueInfo queue) {
                handleRefreshQueue(queue);
            }

            @Override
            public void onCopyQueueName(QueueInfo queue) {
                handleCopyQueueName(queue);
            }
        });

        // EXISTING: Tab Folder (50%)
        tabFolder = new TabFolder(sashForm, SWT.NONE);

        createPropertiesTab();
        createMessagesTab();
        createChartTab();
        createHandlesTab();

        // UPDATED: Three-panel weights (was: 30, 70)
        sashForm.setWeights(new int[]{20, 30, 50});
    }

    private void createPropertiesTab() {
        TabItem propertiesTab = new TabItem(tabFolder, SWT.NONE);
        propertiesTab.setText("Properties");
        propertiesPanel = new QueuePropertiesPanel(tabFolder, SWT.NONE);
        propertiesTab.setControl(propertiesPanel);
    }

    private void createMessagesTab() {
        TabItem messagesTab = new TabItem(tabFolder, SWT.NONE);
        messagesTab.setText("Messages");
        messageBrowserPanel = new MessageBrowserPanel(tabFolder, SWT.NONE, messageService);
        messagesTab.setControl(messageBrowserPanel);
    }

    private void createChartTab() {
        TabItem chartTab = new TabItem(tabFolder, SWT.NONE);
        chartTab.setText("Depth Chart");
        depthChartPanel = new DepthChartPanel(tabFolder, SWT.NONE);
        chartTab.setControl(depthChartPanel);
    }

    private void createHandlesTab() {
        TabItem handlesTab = new TabItem(tabFolder, SWT.NONE);
        handlesTab.setText("Handles");
        queueHandlesPanel = new QueueHandlesPanel(tabFolder, SWT.NONE, queueService);
        handlesTab.setControl(queueHandlesPanel);
    }

    private void createStatusBar() {
        Composite statusBar = new Composite(shell, SWT.NONE);
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout layout = new GridLayout(2, false);
        layout.marginHeight = 2;
        statusBar.setLayout(layout);

        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Not connected");

        alertLabel = new Label(statusBar, SWT.NONE);
        alertLabel.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        alertLabel.setText("");
    }

    private void showConnectionDialog() {
        QueueManagerDialog dialog = new QueueManagerDialog(shell, configManager);
        QueueManagerConfig config = dialog.open();

        if (config != null) {
            connect(config);
        }
    }

    private void connect(QueueManagerConfig config) {
        queueListViewer.showProgress("Connecting to " + config.getQueueManager() + "...");

        new Thread(() -> {
            try {
                connectionManager.connect(config);

                display.asyncExec(() -> {
                    updateStatus("Connected to " + config.getQueueManager());
                    queueListViewer.updateProgress("Loading queues...");
                });

                List<QueueInfo> queues = queueService.getAllQueues();

                display.asyncExec(() -> {
                    queueListViewer.setQueues(queues);
                    queueListViewer.hideProgress();
                    if (depthChartPanel != null) {
                        depthChartPanel.setQueues(queues);
                    }
                });
            } catch (Exception e) {
                log.error("Connection failed", e);
                display.asyncExec(() -> {
                    queueListViewer.hideProgress();
                    showError("Connection Failed", "Failed to connect to queue manager: " + e.getMessage());
                });
            }
        }).start();
    }

    private void disconnect() {
        stopMonitoring();
        connectionManager.disconnect();
        queueListViewer.clearQueues();
        updateStatus("Disconnected");
    }

    private void refreshQueues() {
        if (!connectionManager.isConnected()) {
            return;
        }

        queueListViewer.showProgress("Refreshing queues...");

        new Thread(() -> {
            try {
                List<QueueInfo> queues = queueService.getAllQueues();

                display.asyncExec(() -> {
                    queueListViewer.setQueues(queues);
                    queueListViewer.hideProgress();
                    if (depthChartPanel != null) {
                        depthChartPanel.setQueues(queues);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to refresh queues", e);
                display.asyncExec(() -> {
                    queueListViewer.hideProgress();
                    showError("Error", "Failed to refresh queues: " + e.getMessage());
                });
            }
        }).start();
    }

    private void toggleAutoRefresh(boolean enabled) {
        if (enabled) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    }

    private void startMonitoring() {
        if (queueMonitor == null || !queueMonitor.isRunning()) {
            queueMonitor = new QueueMonitor(queueService, alertManager);
            queueMonitor.setMonitoredQueues(queueListViewer.getQueues());
            queueMonitor.setListener(new QueueMonitor.QueueMonitorListener() {
                @Override
                public void onQueuesUpdated(List<QueueInfo> queues) {
                    display.asyncExec(() -> {
                        queueListViewer.refresh();
                        if (depthChartPanel != null && selectedQueue != null) {
                            depthChartPanel.updateData(selectedQueue);
                        }
                        updateAlertStatus();
                    });
                }

                @Override
                public void onMonitorError(Exception e) {
                    display.asyncExec(() -> showError("Monitor Error", e.getMessage()));
                }
            });
            queueMonitor.start();
        }
    }

    private void stopMonitoring() {
        if (queueMonitor != null) {
            queueMonitor.stopMonitoring();
            queueMonitor = null;
        }
    }

    private void onQueueSelected(QueueInfo queue) {
        this.selectedQueue = queue;
        if (propertiesPanel != null) {
            try {
                queueService.refreshQueueInfo(queue);
            } catch (MQException | IOException | MQDataException e) {
                log.error("Exception during refreshQueueInfo({})", queue.getQueue(), e);
            }
            propertiesPanel.setQueue(queue);
        }
        if (messageBrowserPanel != null) {
            messageBrowserPanel.setQueue(queue);
        }
        if (depthChartPanel != null) {
            depthChartPanel.setSelectedQueue(queue);
        }
        if (queueHandlesPanel != null) {
            queueHandlesPanel.setQueue(queue.getQueue());
        }
    }

    private void onTreeSelection(HierarchyTreeViewer.SelectionEvent event) {
        if (event.type == HierarchyTreeViewer.SelectionType.FOLDER) {
            // Clear queue list and disable detail panels
            queueListViewer.clearQueues();
            if (propertiesPanel != null) {
                propertiesPanel.setQueue(null);
            }
            if (messageBrowserPanel != null) {
                messageBrowserPanel.setQueue(null);
            }
            if (depthChartPanel != null) {
                depthChartPanel.setSelectedQueue(null);
            }
            if (queueHandlesPanel != null) {
                queueHandlesPanel.setQueue(null);
            }
            updateStatus("Folder selected: " + event.node.getName());

        } else if (event.type == HierarchyTreeViewer.SelectionType.QUEUE_BROWSER) {
            final QueueBrowserConfig queueBrowserConfig = event.node.getQueueBrowserConfig();
            final String connectionId = queueBrowserConfig.getQueueManager();
            if (queueBrowserConfig.getDescriptions() == null) {
                log.error("queueBrowserConfig.getDescriptions() null for {}", queueBrowserConfig);
                return;
            }
            final List<QueueInfo> queueInfos = queueBrowserConfig.getDescriptions().entrySet().stream()
                    .map(e -> new QueueInfo(e.getKey(), e.getValue().label()))
                    .toList();
            log.info("number of queues to retrieve: {}", queueInfos.size());
            if (!connectionManager.isConnected(connectionId)) {
                QueueManagerConfig config = findConnectionConfig(connectionId);
                if (config == null) {
                    showError("Configuration Not Found",
                            "Connection configuration not found for: " + connectionId);
                    return;
                }

                // Show progress immediately
                queueListViewer.showProgress("Connecting to " + event.node.getName() + "...");

                // Run connection in background thread
                String qmName = event.node.getName();
                String nodeId = event.node.getId();
                new Thread(() -> {
                    try {
                        // BLOCKING CALL - but on background thread
                        connectionManager.connect(connectionId, config);

                        // Update icon on UI thread
                        display.asyncExec(() -> {
                            hierarchyTreeViewer.updateNodeIcon(nodeId);
                            queueListViewer.updateProgress("Loading queues...");
                        });

                        // Set active and load queues (BLOCKING)
                        connectionManager.setActiveConnection(connectionId);
                        queueService.populateQueueInfos(queueInfos, queueBrowserConfig.isSequencialQueueRequest());

                        // Update UI on UI thread
                        display.asyncExec(() -> {
                            queueListViewer.setQueues(queueInfos);
                            queueListViewer.hideProgress();
                            updateStatus("Connected to " + qmName);

                            // Update panels
                            if (depthChartPanel != null) {
                                depthChartPanel.setQueues(queueInfos);
                            }
                        });

                    } catch (Exception e) {
                        log.error("Connection failed", e);
                        display.asyncExec(() -> {
                            queueListViewer.hideProgress();
                            showError("Connection Failed", e.getMessage());
                            hierarchyTreeViewer.updateNodeIcon(nodeId);
                        });
                    }
                }).start();
                return; // Don't continue with synchronous flow
            }

            // If already connected, just set active and load queues
            connectionManager.setActiveConnection(connectionId);
            loadQueuesAsync(event.node.getName(), queueInfos, queueBrowserConfig.isSequencialQueueRequest());
        }
    }

    private void loadQueuesAsync(String queueManagerName, List<QueueInfo> queueInfos, boolean sequentialQueueRequest) {
        queueListViewer.showProgress("Loading queues from " + queueManagerName + "...");

        new Thread(() -> {
            try {
                queueService.populateQueueInfos(queueInfos, sequentialQueueRequest);

                display.asyncExec(() -> {
                    queueListViewer.setQueues(queueInfos);
                    queueListViewer.hideProgress();
                    updateStatus("Loaded queues from " + queueManagerName);
                    if (depthChartPanel != null) {
                        depthChartPanel.setQueues(queueInfos);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to load queues", e);
                display.asyncExec(() -> {
                    queueListViewer.hideProgress();
                    showError("Error", "Failed to load queues: " + e.getMessage());
                });
            }
        }).start();
    }

    private QueueManagerConfig findConnectionConfig(String name) {
        return configManager.loadConnections().get(name);
    }

    private void loadHierarchy() {
        HierarchyConfig hierarchy = configManager.loadHierarchy();
        if (hierarchy == null) {
            // First time: create default hierarchy from existing connections
            Map<String, QueueManagerConfig> connections = configManager.loadConnections();
            hierarchy = configManager.createDefaultHierarchy(connections);
            configManager.saveHierarchy(hierarchy);
        }
        hierarchyTreeViewer.setHierarchyConfig(hierarchy);
    }

    private void showThresholdDialog() {
        ThresholdConfigDialog dialog = new ThresholdConfigDialog(shell, configManager, queueListViewer.getQueues());
        dialog.open();
    }

    private void showSendMessageDialog() {
        if (selectedQueue != null) {
            handleSendMessage(selectedQueue);
        } else {
            showError("No Queue Selected", "Please select a queue first");
        }
    }

    private void handleSendMessage(QueueInfo queue) {
        SendMessageDialog dialog = new SendMessageDialog(shell, messageService, configManager);
        dialog.open(queue.getQueue());
    }

    private void handleBrowseMessages(QueueInfo queue) {
        // Update selected queue to ensure consistency
        this.selectedQueue = queue;

        // Update the message browser panel with the selected queue
        if (messageBrowserPanel != null) {
            messageBrowserPanel.setQueue(queue);
        }

        // Switch to Messages tab (index 1)
        if (tabFolder != null) {
            tabFolder.setSelection(1);
        }
    }

    private void handleRefreshQueue(QueueInfo queue) {
        queueListViewer.showProgress("Refreshing " + queue.getQueue() + "...");

        new Thread(() -> {
            try {
                // Refresh the queue info from the queue manager
                queueService.refreshQueueInfo(queue);

                display.asyncExec(() -> {
                    // Update the display
                    queueListViewer.refreshQueue(queue);
                    queueListViewer.hideProgress();

                    // If this is the currently selected queue, update panels
                    if (selectedQueue != null && selectedQueue.getQueue().equals(queue.getQueue())) {
                        this.selectedQueue = queue;
                        if (propertiesPanel != null) {
                            propertiesPanel.setQueue(queue);
                        }
                        if (depthChartPanel != null) {
                            depthChartPanel.updateData(queue);
                        }
                    }

                    updateStatus("Queue " + queue.getQueue() + " refreshed");
                });
            } catch (Exception e) {
                log.error("Failed to refresh queue: " + queue.getQueue(), e);
                display.asyncExec(() -> {
                    queueListViewer.hideProgress();
                    showError("Refresh Failed", "Failed to refresh queue: " + e.getMessage());
                });
            }
        }).start();
    }

    private void handleCopyQueueName(QueueInfo queue) {
        Clipboard clipboard = new Clipboard(display);
        try {
            TextTransfer textTransfer = TextTransfer.getInstance();
            clipboard.setContents(
                    new Object[]{queue.getQueue()},
                    new Transfer[]{textTransfer}
            );
            updateStatus("Queue name copied: " + queue.getQueue());
        } finally {
            clipboard.dispose();
        }
    }


    private void clearAlerts() {
        alertManager.clearAlertHistory();
        updateAlertStatus();
    }

    private void updateAlertStatus() {
        int criticalCount = 0;
        int warningCount = 0;

        for (String queueName : alertManager.getAllCurrentAlerts().keySet()) {
            ThresholdConfig.AlertLevel level = alertManager.getCurrentAlertLevel(queueName);
            if (level == ThresholdConfig.AlertLevel.CRITICAL) {
                criticalCount++;
            } else if (level == ThresholdConfig.AlertLevel.WARNING) {
                warningCount++;
            }
        }

        if (criticalCount > 0) {
            alertLabel.setText(String.format("Alerts: %d critical, %d warning", criticalCount, warningCount));
            alertLabel.setForeground(display.getSystemColor(SWT.COLOR_RED));
        } else if (warningCount > 0) {
            alertLabel.setText(String.format("Alerts: %d warning", warningCount));
            alertLabel.setForeground(display.getSystemColor(SWT.COLOR_DARK_YELLOW));
        } else {
            alertLabel.setText("");
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        box.setText(title);
        box.setMessage(message);
        box.open();
    }

    private void showAbout() {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText("About");
        box.setMessage("IBM MQ Queue Visualizer GUI\nVersion 1.0\n\n(c) Aquila");
        box.open();
    }

    /**
     * Import configuration from a JSON file and add to selected hierarchy node.
     */
    private void importConfiguration() {
        // Show file dialog to select import file
        org.eclipse.swt.widgets.FileDialog dialog = new org.eclipse.swt.widgets.FileDialog(shell, SWT.OPEN);
        dialog.setText("Import Configuration");
        dialog.setFilterExtensions(new String[]{"*.json", "*.*"});
        dialog.setFilterNames(new String[]{"JSON Files (*.json)", "All Files (*.*)"});

        String filePath = dialog.open();
        if (filePath == null) {
            return; // User cancelled
        }

        log.info("Importing configuration from: {}", filePath);

        final RootImportNode rootImportNode;
        try {
            rootImportNode = RootImportNode.from(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Import queue managers
        final Map<String, QueueManagerConfigNode> importedQueueManagers = rootImportNode.getQueueManagers();
        if (importedQueueManagers != null && !importedQueueManagers.isEmpty()) {
            Map<String, QueueManagerConfig> existingQueueManagers = configManager.loadConnections();

            int newCount = 0;
            for (Map.Entry<String, QueueManagerConfigNode> entry : importedQueueManagers.entrySet()) {
                if (!existingQueueManagers.containsKey(entry.getKey())) {
                    existingQueueManagers.put(entry.getKey(), new QueueManagerConfig(entry.getValue()));
                    newCount++;
                }
            }

            if (newCount > 0) {
                configManager.saveConnections(existingQueueManagers);
                log.info("Imported {} new queue manager(s)", newCount);
            }
        }

        if (rootImportNode.getHierarchy() != null) {
            // Ask user where to add the imported hierarchy
            MessageBox locationBox = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
            locationBox.setText("Import Location");
            locationBox.setMessage("Where would you like to add the imported hierarchy?\n\n" +
                    "YES - Add under currently selected node\n" +
                    "NO - Add at root level\n" +
                    "CANCEL - Skip hierarchy import");
            int choice = locationBox.open();
            if (choice != SWT.CANCEL) {
                HierarchyConfig currentHierarchy = hierarchyTreeViewer.getHierarchyConfig();
                if (currentHierarchy == null) {
                    currentHierarchy = new HierarchyConfig();
                }
                if (choice == SWT.YES) {
                    log.info("YES ...");
                    HierarchyConfig importedHierarchy = toHierarchyConfig(rootImportNode, currentHierarchy.getSelectedNodeId());
                    // Save and refresh
                    configManager.saveHierarchy(currentHierarchy);
                    hierarchyTreeViewer.setHierarchyConfig(currentHierarchy);
                    log.info("Successfully imported hierarchy");
                }
            }
        }
    }

    public HierarchyConfig toHierarchyConfig(RootImportNode rootImportNode, String parentId) {
        final HierarchyConfig hierarchyConfig = new HierarchyConfig();
        final Map<String, HierarchyNode> nodes = hierarchyConfig.getNodes();
        processRecursive(rootImportNode.getHierarchy(), parentId);
        return hierarchyConfig;
    }

    private void processRecursive(Map<String, ImportNode> importChildrens, String parentId) {
        importChildrens.forEach((key, value) -> {
            switch (value) {
                case FolderImportNode folder -> {
                    HierarchyNode hierarchyNode = this.hierarchyTreeViewer.addFolder(key, parentId);
                    processRecursive(folder.getChildren(), hierarchyNode.getId());
                }
                case QueuesImportNode queuesImportNode -> {
                    this.hierarchyTreeViewer.addQueues(key, queuesImportNode, parentId);
                }
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }
        });

    }

    /**
     * Import configuration from a JSON file and add to selected hierarchy node.
     */
    private void importConfiguration1() {
        // Show file dialog to select import file
        org.eclipse.swt.widgets.FileDialog dialog = new org.eclipse.swt.widgets.FileDialog(shell, SWT.OPEN);
        dialog.setText("Import Configuration");
        dialog.setFilterExtensions(new String[]{"*.json", "*.*"});
        dialog.setFilterNames(new String[]{"JSON Files (*.json)", "All Files (*.*)"});

        String filePath = dialog.open();
        if (filePath == null) {
            return; // User cancelled
        }

        log.info("Importing configuration from: {}", filePath);

        // Import the configuration
        ImportConfig importConfig = ImportExportUtil.importFromFile(filePath);

        if (importConfig == null) {
            showError("Import Failed", "Failed to read import file. Please check the file format.");
            return;
        }

        // Import queue managers
        Map<String, QueueManagerConfig> importedQueueManagers = importConfig.getQueueManagers();
        if (importedQueueManagers != null && !importedQueueManagers.isEmpty()) {
            Map<String, QueueManagerConfig> existingQueueManagers = configManager.loadConnections();

            int newCount = 0;
            for (Map.Entry<String, QueueManagerConfig> entry : importedQueueManagers.entrySet()) {
                if (!existingQueueManagers.containsKey(entry.getKey())) {
                    existingQueueManagers.put(entry.getKey(), entry.getValue());
                    newCount++;
                }
            }

            if (newCount > 0) {
                configManager.saveConnections(existingQueueManagers);
                log.info("Imported {} new queue manager(s)", newCount);
            }
        }

        // Import hierarchy
        if (importConfig.getHierarchy() != null) {
            // Ask user where to add the imported hierarchy
            MessageBox locationBox = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
            locationBox.setText("Import Location");
            locationBox.setMessage("Where would you like to add the imported hierarchy?\n\n" +
                    "YES - Add under currently selected node\n" +
                    "NO - Add at root level\n" +
                    "CANCEL - Skip hierarchy import");

            int choice = locationBox.open();

            if (choice != SWT.CANCEL) {
                HierarchyConfig currentHierarchy = hierarchyTreeViewer.getHierarchyConfig();
                if (currentHierarchy == null) {
                    currentHierarchy = new HierarchyConfig();
                }

                // Convert imported hierarchy to internal format
                HierarchyConfig importedHierarchy = importConfig.toHierarchyConfig();

                String parentId = null;
                if (choice == SWT.YES) {
                    // Add under selected node
                    HierarchyNode selectedNode = hierarchyTreeViewer.getSelectedNode();
                    if (selectedNode != null) {
                        if (selectedNode.isFolder()) {
                            parentId = selectedNode.getId();
                        } else {
                            MessageBox infoBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
                            infoBox.setText("Invalid Selection");
                            infoBox.setMessage("Can only add hierarchy under a folder node. Adding at root level instead.");
                            infoBox.open();
                        }
                    }
                }

                // Add all root nodes from imported hierarchy to current hierarchy
                for (String rootId : importedHierarchy.getRootNodeIds()) {
                    HierarchyNode rootNode = importedHierarchy.getNode(rootId);
                    if (rootNode != null) {
                        mergeHierarchyNode(rootNode, importedHierarchy, currentHierarchy, parentId);
                    }
                }

                // Save and refresh
                configManager.saveHierarchy(currentHierarchy);
                hierarchyTreeViewer.setHierarchyConfig(currentHierarchy);

                log.info("Successfully imported hierarchy");
            }
        }

        // Show success message
        MessageBox successBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        successBox.setText("Import Complete");
        successBox.setMessage("Configuration imported successfully!");
        successBox.open();
    }

    /**
     * Recursively merge a hierarchy node and its children into the target hierarchy.
     */
    private void mergeHierarchyNode(HierarchyNode sourceNode, HierarchyConfig sourceHierarchy,
                                    HierarchyConfig targetHierarchy, String targetParentId) {
        // Create a copy of the node
        HierarchyNode newNode = new HierarchyNode(sourceNode.getType(), sourceNode.getName());
        newNode.setQueueBrowserConfig(sourceNode.getQueueBrowserConfig());

        // Add to target hierarchy
        targetHierarchy.addNode(newNode, targetParentId);

        // Copy queue browser config if it exists
        if (sourceNode.getQueueBrowserConfig() != null) {
            configManager.save(newNode.getId(), sourceNode.getQueueBrowserConfig());
        }

        // Recursively merge children
        for (String childId : sourceNode.getChildIds()) {
            HierarchyNode childNode = sourceHierarchy.getNode(childId);
            if (childNode != null) {
                mergeHierarchyNode(childNode, sourceHierarchy, targetHierarchy, newNode.getId());
            }
        }
    }

    /**
     * Export current configuration to a JSON file.
     */
    private void exportConfiguration() {
        // Show file dialog to select export location
        org.eclipse.swt.widgets.FileDialog dialog = new org.eclipse.swt.widgets.FileDialog(shell, SWT.SAVE);
        dialog.setText("Export Configuration");
        dialog.setFilterExtensions(new String[]{"*.json", "*.*"});
        dialog.setFilterNames(new String[]{"JSON Files (*.json)", "All Files (*.*)"});
        dialog.setFileName("export.json");

        String filePath = dialog.open();
        if (filePath == null) {
            return; // User cancelled
        }

        log.info("Exporting configuration to: {}", filePath);

        // Get current configuration
        HierarchyConfig hierarchyConfig = hierarchyTreeViewer.getHierarchyConfig();
        Map<String, QueueManagerConfig> queueManagers = configManager.loadConnections();

        // Export
        boolean success = ImportExportUtil.exportToFile(filePath, hierarchyConfig, queueManagers);

        if (success) {
            MessageBox successBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
            successBox.setText("Export Complete");
            successBox.setMessage("Configuration exported successfully to:\n" + filePath);
            successBox.open();
        } else {
            showError("Export Failed", "Failed to export configuration. Please check the file path and permissions.");
        }
    }

    private void cleanup() {
        // Save hierarchy state (expansion, selection)
        if (hierarchyTreeViewer != null) {
            configManager.saveHierarchy(hierarchyTreeViewer.getHierarchyConfig());
        }

        stopMonitoring();
        connectionManager.disconnectAll();  // Disconnect all connections
    }

    public void open() {
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

}
