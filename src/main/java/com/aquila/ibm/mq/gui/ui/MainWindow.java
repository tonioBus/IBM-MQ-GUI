package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.ConnectionConfig;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import com.aquila.ibm.mq.gui.mq.MQConnectionManager;
import com.aquila.ibm.mq.gui.mq.MessageService;
import com.aquila.ibm.mq.gui.mq.QueueMonitor;
import com.aquila.ibm.mq.gui.mq.QueueService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MainWindow {
    private static final Logger logger = LoggerFactory.getLogger(MainWindow.class);

    private final Display display;
    private final Shell shell;
    private final ConfigManager configManager;
    private final MQConnectionManager connectionManager;
    private final QueueService queueService;
    private final MessageService messageService;
    private final AlertManager alertManager;
    private QueueMonitor queueMonitor;

    private QueueManagerTreeViewer queueManagerTreeViewer;
    private QueueListViewer queueListViewer;
    private TabFolder tabFolder;
    private QueuePropertiesPanel propertiesPanel;
    private MessageBrowserPanel messageBrowserPanel;
    private SendMessageDialog sendMessageDialog;
    private DepthChartPanel depthChartPanel;
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
        shell.setSize(1200, 800);
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
        queueManagerTreeViewer = new QueueManagerTreeViewer(
            sashForm, SWT.BORDER, connectionManager, configManager);
        queueManagerTreeViewer.addSelectionListener(this::onTreeSelection);

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
        ConnectionDialog dialog = new ConnectionDialog(shell, configManager);
        ConnectionConfig config = dialog.open();

        if (config != null) {
            connect(config);
        }
    }

    private void connect(ConnectionConfig config) {
        try {
            connectionManager.connect(config);
            updateStatus("Connected to " + config.getQueueManager());
            loadQueues();
        } catch (Exception e) {
            logger.error("Connection failed", e);
            showError("Connection Failed", "Failed to connect to queue manager: " + e.getMessage());
        }
    }

    private void disconnect() {
        stopMonitoring();
        connectionManager.disconnect();
        queueListViewer.clearQueues();
        updateStatus("Disconnected");
    }

    private void loadQueues() {
        try {
            List<QueueInfo> queues = queueService.getAllQueues();
            queueListViewer.setQueues(queues);
            if (depthChartPanel != null) {
                depthChartPanel.setQueues(queues);
            }
        } catch (Exception e) {
            logger.error("Failed to load queues", e);
            showError("Error", "Failed to load queues: " + e.getMessage());
        }
    }

    private void refreshQueues() {
        if (!connectionManager.isConnected()) {
            return;
        }
        loadQueues();
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
            propertiesPanel.setQueue(queue);
        }
        if (messageBrowserPanel != null) {
            messageBrowserPanel.setQueue(queue);
        }
        if (depthChartPanel != null) {
            depthChartPanel.setSelectedQueue(queue);
        }
    }

    private void onTreeSelection(QueueManagerTreeViewer.SelectionEvent event) {
        if (event.type == QueueManagerTreeViewer.SelectionType.FOLDER) {
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
            updateStatus("Folder selected: " + event.node.getName());

        } else if (event.type == QueueManagerTreeViewer.SelectionType.QUEUE_MANAGER) {
            String connectionId = event.node.getConnectionConfigId();

            // Connect if not already connected
            if (!connectionManager.isConnected(connectionId)) {
                ConnectionConfig config = findConnectionConfig(connectionId);
                if (config != null) {
                    try {
                        connectionManager.connect(connectionId, config);
                        queueManagerTreeViewer.updateNodeIcon(event.node.getId());
                    } catch (Exception e) {
                        showError("Connection Failed", e.getMessage());
                        return;
                    }
                } else {
                    showError("Configuration Not Found",
                        "Connection configuration not found for: " + connectionId);
                    return;
                }
            }

            // Set active connection and load queues
            connectionManager.setActiveConnection(connectionId);
            loadQueues();
            updateStatus("Connected to " + event.node.getName());
        }
    }

    private ConnectionConfig findConnectionConfig(String name) {
        return configManager.loadConnections().stream()
            .filter(c -> name.equals(c.getName()))
            .findFirst()
            .orElse(null);
    }

    private void loadHierarchy() {
        com.aquila.ibm.mq.gui.model.HierarchyConfig hierarchy = configManager.loadHierarchy();
        if (hierarchy == null) {
            // First time: create default hierarchy from existing connections
            List<ConnectionConfig> connections = configManager.loadConnections();
            hierarchy = configManager.createDefaultHierarchy(connections);
            configManager.saveHierarchy(hierarchy);
        }
        queueManagerTreeViewer.setHierarchy(hierarchy);
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
        SendMessageDialog dialog = new SendMessageDialog(shell, messageService);
        dialog.open(queue.getName());
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
        try {
            // Refresh the queue info from the queue manager
            queueService.refreshQueueInfo(queue);

            // Update the display
            queueListViewer.refreshQueue(queue);

            // If this is the currently selected queue, update the properties panel
            if (selectedQueue != null && selectedQueue.getName().equals(queue.getName())) {
                this.selectedQueue = queue;
                if (propertiesPanel != null) {
                    propertiesPanel.setQueue(queue);
                }
                if (depthChartPanel != null) {
                    depthChartPanel.updateData(queue);
                }
            }

            updateStatus("Queue " + queue.getName() + " refreshed");
        } catch (Exception e) {
            logger.error("Failed to refresh queue: " + queue.getName(), e);
            showError("Refresh Failed", "Failed to refresh queue: " + e.getMessage());
        }
    }

    private void handleCopyQueueName(QueueInfo queue) {
        Clipboard clipboard = new Clipboard(display);
        try {
            TextTransfer textTransfer = TextTransfer.getInstance();
            clipboard.setContents(
                new Object[]{queue.getName()},
                new Transfer[]{textTransfer}
            );
            updateStatus("Queue name copied: " + queue.getName());
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

    private void cleanup() {
        // Save hierarchy state (expansion, selection)
        if (queueManagerTreeViewer != null) {
            configManager.saveHierarchy(queueManagerTreeViewer.getHierarchy());
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

    public Shell getShell() {
        return shell;
    }
}
