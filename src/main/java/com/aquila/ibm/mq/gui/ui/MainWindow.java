/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * Main application window built with Eclipse SWT.
 * Coordinates all UI components including hierarchy tree, queue list,
 * properties panel, message browser, and depth charts.
 */
package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.config.Configuration;
import com.aquila.ibm.mq.gui.importation.*;
import com.aquila.ibm.mq.gui.model.*;
import com.aquila.ibm.mq.gui.model.node.HierarchyNode;
import com.aquila.ibm.mq.gui.model.node.QueueNode;
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
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class MainWindow implements UpdateQueueInfo {

    @Getter
    private static MainWindow instance;

    private final Display display;
    @Getter
    private final Shell shell;
    private final Configuration configuration;
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
    private DepthChartPanel depthChartPanel;
    private QueueHandlesPanel queueHandlesPanel;
    private Label statusLabel;
    private Label alertLabel;
    private QueueInfo selectedQueue;

    public MainWindow(Display display) {
        instance = this;
        this.display = display;
        this.configuration = new Configuration();
        this.connectionManager = new MQConnectionManager();
        this.queueService = new QueueService(this, connectionManager);
        this.messageService = new MessageService(connectionManager);
        this.alertManager = new AlertManager(configuration);

        shell = new Shell(display);
        shell.setText("IBM MQ Queue Manager GUI");
        shell.setSize(1600, 800);
        shell.setLayout(new GridLayout());
        try {
            shell.setImages(new Image[]{
                    new Image(display, getClass().getResourceAsStream("/icons/Aquila-16.png")),
                    new Image(display, getClass().getResourceAsStream("/icons/Aquila-32.png")),
                    new Image(display, getClass().getResourceAsStream("/icons/Aquila-48.png")),
                    new Image(display, getClass().getResourceAsStream("/icons/Aquila-256.png"))
            });
        } catch (Exception e) {
            log.warn("Impossible de charger l'icône de l'application", e);
        }
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

        MenuItem exportSelectedItem = new MenuItem(fileMenu, SWT.PUSH);
        exportSelectedItem.setText("Export &Selected...");
        exportSelectedItem.addListener(SWT.Selection, e -> exportSelectedConfiguration());

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
                sashForm, SWT.BORDER, connectionManager, configuration);
        hierarchyTreeViewer.addSelectionListener(this::onTreeSelection);
        hierarchyTreeViewer.setContextMenuActionListener(new HierarchyTreeViewer.ContextMenuActionListener() {
            @Override
            public void onImportConfiguration() {
                importConfiguration();
            }

            @Override
            public void onExportConfiguration() {
                exportConfiguration();
            }

            @Override
            public void onExportSelectedConfiguration(HierarchyNode node) {
                exportSelectedConfiguration();
            }
        });

        // EXISTING: Queue List (30%)
        queueListViewer = new QueueListViewer(sashForm, SWT.BORDER, alertManager);
        queueListViewer.addSelectionListener(this::onQueueSelected);

        queueListViewer.setAutoRefreshListener(new QueueListViewer.AutoRefreshListener() {
            @Override
            public void onAutoRefreshToggled(boolean enabled) {
                toggleAutoRefresh(enabled);
            }

            @Override
            public void onRefreshIntervalChanged(int intervalMs) {
                if (queueMonitor != null && queueMonitor.isRunning()) {
                    queueMonitor.setRefreshInterval(intervalMs);
                }
            }
        });
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
        sashForm.setWeights(new int[]{15, 50, 35});
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
        QueueManagerDialog dialog = new QueueManagerDialog(shell, configuration);
        QueueManagerConfig config = dialog.open();

        if (config != null) {
            connect(config);
        }
    }

    private void connect(QueueManagerConfig config) {

        new Thread(() -> {
            try {
                connectionManager.connect(config);

                display.asyncExec(() -> {
                    updateStatus("Connected to " + config.getQueueManager());
                });

                List<QueueInfo> queues = queueService.getAllQueues("*");

                display.asyncExec(() -> {
                    queueListViewer.setQueues(queues, true);
                    if (depthChartPanel != null) {
                        depthChartPanel.setQueues(queues);
                    }
                });
            } catch (Exception e) {
                log.error("Connection failed", e);
                display.asyncExec(() -> {
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

        // queueListViewer.showProgress("Refreshing queues...");

        new Thread(() -> {
            try {
                List<QueueInfo> queues = queueService.getAllQueues("*");

                display.asyncExec(() -> {
                    queueListViewer.setQueues(queues, false);
                    // queueListViewer.hideProgress();
                    if (depthChartPanel != null) {
                        depthChartPanel.setQueues(queues);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to refresh queues", e);
                display.asyncExec(() -> {
                    // queueListViewer.hideProgress();
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
            queueMonitor = new QueueMonitor(this, queueService, alertManager);
            queueMonitor.setRefreshInterval(queueListViewer.getSelectedRefreshInterval());
            queueMonitor.setMonitoredQueues(queueListViewer.getQueues());
            queueMonitor.setListener(new QueueMonitor.QueueMonitorListener() {
                @Override
                public void onQueuesUpdated(List<QueueInfo> queues) {
                    display.asyncExec(() -> {
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
            Display.getDefault().asyncExec(() -> queueMonitor.start());
        }
    }

    private void stopMonitoring() {
        if (queueMonitor != null) {
            queueMonitor.stopMonitoring();
            queueMonitor = null;
        }
    }

    @Override
    public void update(QueueInfo queueInfo) {
        if(!queueListViewer.isAutoRefreshEnabled()) return;
        log.info("update called for queue: {} depth:{}", queueInfo.getQueue(), queueInfo.getCurrentDepth());
        queueListViewer.refreshDynamicFields(queueInfo);
        Display.getDefault().asyncExec(() -> {
            queueListViewer.refreshSort();
        });
    }

    private void onQueueSelected(QueueInfo queue) {
        this.selectedQueue = queue;
        log.info("onQueueSelected: {}", queue.getQueue());

        if (propertiesPanel != null) {
            try {
                queueService.refreshQueueInfo(queue);
            } catch (MQException | IOException | MQDataException e) {
                log.error("Exception during refreshQueueInfo({})", queue.getQueue(), e);
            }
            propertiesPanel.setQueue(queue);
        }
        queueListViewer.refreshSelectedQueue();
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
            final QueueNode queueNode = event.node.getQueueNode();
            final String connectionId = queueNode.getQueueManager();
            refreshQueueListPrivate(queueNode, connectionId, true);
        }
    }

    public void refreshQueueList() {
        final HierarchyNode node = this.hierarchyTreeViewer.getLastSelectedNode();
        log.info("refreshQueueList: {}", node);
        if (node != null && node.getQueueNode() != null) {
            final QueueNode queueNode = node.getQueueNode();
            refreshQueueListPrivate(queueNode, queueNode.getQueueManager(), true);
        }
    }

    private void refreshQueueListPrivate(final QueueNode queueNode,
                                         final String connectionId,
                                         final boolean forceRefresh) {
        if (queueNode.getDescriptions() == null) {
            log.error("queueBrowserConfig.getDescriptions() null for {}", queueNode);
            return;
        }
        final List<QueueInfo> queueInfos = queueNode.getDescriptions().entrySet().stream()
                .map(e -> new QueueInfo(e.getKey(), e.getValue().label()))
                .toList();
        log.info("number of queues to retrieve: {}", queueInfos.size());
        queueListViewer.setQueues(queueInfos, forceRefresh);
        loadQueuesAsync(queueNode.getQueueManager(), queueInfos, queueNode.getNbThread(), queueNode.isSequencialQueueRequest());
        if (!connectionManager.isConnected(connectionId)) {
            QueueManagerConfig config = findConnectionConfig(connectionId);
            if (config == null) {
                showError("Configuration Not Found",
                        "Connection configuration not found for: " + connectionId);
                return;
            }
            try {
                connectionManager.connect(connectionId, config);
            } catch (MQException e) {
                log.error("Connection failed", e);
            }
            return;
        }
        // If already connected, just set active and load queues
        connectionManager.setActiveConnection(connectionId);
        // Display.getDefault().syncExec(() -> loadQueuesAsync(nodeName, queueInfos, queueNode.getNbThread(), queueNode.isSequencialQueueRequest()));
    }

    private void loadQueuesAsync(String queueManagerName, List<QueueInfo> queueInfos, int nbThread, boolean sequentialQueueRequest) {

        new Thread(() -> {
            try {
                queueService.populateAllQueueInfosShort(queueInfos);
            } catch (Exception e) {
                log.error("Failed to load queues", e);
                display.asyncExec(() -> {
                    showError("Error", "Failed to load queues: " + e.getMessage());
                });
            }
        }).start();
    }

    private QueueManagerConfig findConnectionConfig(String name) {
        return configuration.loadConnections().get(name);
    }

    private void loadHierarchy() {
        HierarchyConfig hierarchy = configuration.loadHierarchy();
        if (hierarchy == null) {
            // First time: create default hierarchy from existing connections
            Map<String, QueueManagerConfig> connections = configuration.loadConnections();
            hierarchy = configuration.createDefaultHierarchy(connections);
            configuration.saveHierarchy(hierarchy);
        }
        hierarchyTreeViewer.setHierarchyConfig(configuration, hierarchy);
    }

    private void showThresholdDialog() {
        ThresholdConfigDialog dialog = new ThresholdConfigDialog(shell, configuration, queueListViewer.getQueues());
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
        SendMessageDialog dialog = new SendMessageDialog(shell, messageService, configuration);
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
        // queueListViewer.showProgress("Refreshing " + queue.getQueue() + "...");

        new Thread(() -> {
            try {
                // Refresh the queue info from the queue manager
                queueService.refreshQueueInfo(queue);

                display.asyncExec(() -> {
                    // Update the display
                    queueListViewer.refreshQueue(queue);
                    // queueListViewer.hideProgress();

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
                    // queueListViewer.hideProgress();
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
        Shell aboutShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        aboutShell.setText("About IBM MQ GUI");
        aboutShell.setLayout(new GridLayout(2, false));

        // Set the window icon
        try {
            Image windowIcon = new Image(display, getClass().getResourceAsStream("/icons/Aquila-32.ico"));
            aboutShell.setImage(windowIcon);
            aboutShell.addDisposeListener(e -> windowIcon.dispose());
        } catch (Exception e) {
            log.warn("Failed to load about dialog icon", e);
        }

        // Display the Aquila icon in the dialog (scaled to 1/4 size)
        Label iconLabel = new Label(aboutShell, SWT.NONE);
        try {
            Image originalIcon = new Image(display, getClass().getResourceAsStream("/icons/Aquila-32.ico"));
            Rectangle bounds = originalIcon.getBounds();
            int newWidth = bounds.width / 4;
            int newHeight = bounds.height / 4;
            Image scaledIcon = new Image(display, newWidth, newHeight);
            GC gc = new GC(scaledIcon);
            gc.setAntialias(SWT.ON);
            gc.setInterpolation(SWT.HIGH);
            gc.drawImage(originalIcon, 0, 0, bounds.width, bounds.height, 0, 0, newWidth, newHeight);
            gc.dispose();
            originalIcon.dispose();
            iconLabel.setImage(scaledIcon);
            iconLabel.addDisposeListener(e -> scaledIcon.dispose());
        } catch (Exception e) {
            log.warn("Failed to load about icon image", e);
        }
        iconLabel.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

        // About text
        Label textLabel = new Label(aboutShell, SWT.NONE);
        textLabel.setText("""
                IBM MQ GUI
                Desktop application for IBM MQ Browsing
                Version 1.0
                
                Author: Anthony Bussani
                GitHub: https://github.com/tonioBus
                
                Licensed under the MIT License.
                Copyright (c) 2026 Anthony Bussani
                
                Permission is hereby granted, free of charge, to any person
                obtaining a copy of this software to use, copy, modify, merge,
                publish, distribute, sublicense, and/or sell copies of the Software.
                
                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
                """);
        textLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

        // OK button
        Button okButton = new Button(aboutShell, SWT.PUSH);
        okButton.setText("OK");
        GridData buttonData = new GridData(SWT.CENTER, SWT.CENTER, false, false, 2, 1);
        buttonData.widthHint = 80;
        okButton.setLayoutData(buttonData);
        okButton.addListener(SWT.Selection, e -> aboutShell.close());

        aboutShell.setDefaultButton(okButton);
        aboutShell.pack();

        // Center the dialog on the parent shell
        int x = shell.getLocation().x + (shell.getSize().x - aboutShell.getSize().x) / 2;
        int y = shell.getLocation().y + (shell.getSize().y - aboutShell.getSize().y) / 2;
        aboutShell.setLocation(x, y);

        aboutShell.open();
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
            Map<String, QueueManagerConfig> existingQueueManagers = configuration.loadConnections();

            int newCount = 0;
            for (Map.Entry<String, QueueManagerConfigNode> entry : importedQueueManagers.entrySet()) {
                if (!existingQueueManagers.containsKey(entry.getKey())) {
                    existingQueueManagers.put(entry.getKey(), new QueueManagerConfig(entry.getValue()));
                    newCount++;
                }
            }

            if (newCount > 0) {
                configuration.saveConnections(existingQueueManagers);
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
                    configuration.saveHierarchy(currentHierarchy);
                    hierarchyTreeViewer.setHierarchyConfig(configuration, currentHierarchy);
                    log.info("Successfully imported hierarchy");
                }
            }
        }
    }

    public HierarchyConfig toHierarchyConfig(RootImportNode rootImportNode, String parentId) {
        final HierarchyConfig hierarchyConfig = new HierarchyConfig();
        processRecursive(rootImportNode.getHierarchy(), parentId);
        return hierarchyConfig;
    }

    private void processRecursive(Map<String, ImportNode> importChildrens, String parentId) {
        importChildrens.forEach((key, importNode) -> {
            switch (importNode) {
                case FolderImportNode folder -> {
                    HierarchyNode hierarchyNode = this.hierarchyTreeViewer.addFolder(key, parentId);
                    processRecursive(folder.getChildren(), hierarchyNode.getId());
                }
                case QueuesImportNode queuesImportNode -> {
                    this.hierarchyTreeViewer.addQueues(key, queuesImportNode, parentId);
                }
                default -> throw new IllegalStateException("Unexpected value: " + importNode);
            }
        });

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
        Map<String, QueueManagerConfig> queueManagers = configuration.loadConnections();

        // Ensure all QueueBrowserConfigs are loaded for all nodes
        for (String rootId : hierarchyConfig.getRootNodeIds()) {
            HierarchyNode rootNode = hierarchyConfig.getNode(rootId);
            if (rootNode != null) {
                loadQueueBrowserConfigsForSubtree(rootNode, hierarchyConfig);
            }
        }

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

    /**
     * Export selected hierarchy node (and all its children) to a JSON file.
     * Only includes queue managers that are referenced by the exported hierarchy.
     */
    private void exportSelectedConfiguration() {
        // Get the selected node
        HierarchyNode selectedNode = hierarchyTreeViewer.getSelectedNode();

        if (selectedNode == null) {
            showError("No Selection", "Please select a folder or queue browser to export.");
            return;
        }

        // Show file dialog to select export location
        org.eclipse.swt.widgets.FileDialog dialog = new org.eclipse.swt.widgets.FileDialog(shell, SWT.SAVE);
        dialog.setText("Export Selected Configuration");
        dialog.setFilterExtensions(new String[]{"*.json", "*.*"});
        dialog.setFilterNames(new String[]{"JSON Files (*.json)", "All Files (*.*)"});
        dialog.setFileName(selectedNode.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");

        String filePath = dialog.open();
        if (filePath == null) {
            return; // User cancelled
        }

        log.info("Exporting selected node '{}' to: {}", selectedNode.getName(), filePath);

        // Get current configuration
        HierarchyConfig hierarchyConfig = hierarchyTreeViewer.getHierarchyConfig();
        Map<String, QueueManagerConfig> queueManagers = configuration.loadConnections();

        // Ensure all QueueBrowserConfigs are loaded for the subtree
        loadQueueBrowserConfigsForSubtree(selectedNode, hierarchyConfig);

        // Export the selected node
        boolean success = ImportExportUtil.exportSelectedToFile(filePath, selectedNode, hierarchyConfig, queueManagers);

        if (success) {
            MessageBox successBox = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
            successBox.setText("Export Complete");
            successBox.setMessage("Selected configuration exported successfully to:\n" + filePath);
            successBox.open();
        } else {
            showError("Export Failed", "Failed to export configuration. Please check the file path and permissions.");
        }
    }

    /**
     * Recursively load QueueBrowserConfigs for all QUEUE nodes in the subtree.
     */
    private void loadQueueBrowserConfigsForSubtree(HierarchyNode node, HierarchyConfig hierarchyConfig) {
        if (node == null) {
            return;
        }

        // If this is a QUEUE node, ensure its QueueBrowserConfig is loaded
        if (node.isQueue() && node.getQueueNode() == null) {
            QueueNode config = configuration.getQueueBrowserConfigMap().get(node.getId());
            if (config != null) {
                node.setQueueNode(config);
            }
        }

        // Recursively process children
        for (String childId : node.getChildIds()) {
            HierarchyNode childNode = hierarchyConfig.getNode(childId);
            if (childNode != null) {
                loadQueueBrowserConfigsForSubtree(childNode, hierarchyConfig);
            }
        }
    }

    private void cleanup() {
        // Save hierarchy state (expansion, selection)
        if (hierarchyTreeViewer != null) {
            configuration.saveHierarchy(hierarchyTreeViewer.getHierarchyConfig());
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

    public void refreshSingleQueue(String queue) {
    }

}
