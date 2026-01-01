package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.ConnectionConfig;
import com.aquila.ibm.mq.gui.model.HierarchyConfig;
import com.aquila.ibm.mq.gui.mq.QueueService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for selecting a queue manager connection to add to the hierarchy.
 */
public class QueueManagerSelectionDialog {
    private static final Logger logger = LoggerFactory.getLogger(QueueManagerSelectionDialog.class);

    private final Shell parentShell;
    private final ConfigManager configManager;
    private final HierarchyConfig hierarchy;
    private Shell shell;
    private org.eclipse.swt.widgets.List connectionList;
    private List<ConnectionConfig> availableConnections;
    private ConnectionConfig selectedConnection;

    public QueueManagerSelectionDialog(Shell parent, ConfigManager configManager, HierarchyConfig hierarchy) {
        this.parentShell = parent;
        this.configManager = configManager;
        this.hierarchy = hierarchy;
    }

    /**
     * Open the dialog and return the selected connection, or null if cancelled.
     */
    public ConnectionConfig open() {
        loadAvailableConnections();

        if (availableConnections.isEmpty()) {
            // No connections available - offer to create one
            MessageBox box = new MessageBox(parentShell, SWT.ICON_INFORMATION | SWT.YES | SWT.NO);
            box.setText("No Connections Available");
            box.setMessage("No Queue Manager detected.\n\nWould you like to create a new connection first?");

            if (box.open() == SWT.YES) {
                ConnectionDialog dialog = new ConnectionDialog(parentShell, configManager);
                ConnectionConfig newConfig = dialog.open();
                return newConfig;
            }
            return null;
        }

        createShell();
        createContents();

        shell.pack();
        shell.setSize(500, 400);

        // Center on parent
        shell.setLocation(
            parentShell.getLocation().x + (parentShell.getSize().x - shell.getSize().x) / 2,
            parentShell.getLocation().y + (parentShell.getSize().y - shell.getSize().y) / 2
        );

        shell.open();

        Display display = parentShell.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        return selectedConnection;
    }

    private void loadAvailableConnections() {
        List<ConnectionConfig> allConnections = configManager.loadConnections();
        availableConnections = new ArrayList<>();

        // Filter out connections already in the hierarchy
        for (ConnectionConfig config : allConnections) {
            String configName = config.getName();
            if (!hierarchy.containsConnectionConfig(configName)) {
                availableConnections.add(config);
            }
        }
    }

    private void createShell() {
        shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Add Queue Browser");
        shell.setLayout(new GridLayout());
    }

    private void createContents() {
        // Instructions
        Label instructionLabel = new Label(shell, SWT.WRAP);
        instructionLabel.setText("Select a queue manager connection to add to the hierarchy:");
        instructionLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        // Connection list
        connectionList = new org.eclipse.swt.widgets.List(shell, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        connectionList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        for (ConnectionConfig config : availableConnections) {
            String displayName = config.getName() != null && !config.getName().isEmpty()
                ? config.getName()
                : config.getQueueManager() + "@" + config.getHost();
            connectionList.add(displayName);
        }

        // Double-click to select
        connectionList.addListener(SWT.MouseDoubleClick, e -> onSelect());

        // Button bar
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.BOTTOM, true, false));
        GridLayout buttonLayout = new GridLayout(3, false);
        buttonLayout.marginWidth = 0;
        buttonBar.setLayout(buttonLayout);

        Button newButton = new Button(buttonBar, SWT.PUSH);
        newButton.setText("New Connection...");
        newButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        newButton.addListener(SWT.Selection, e -> onNewConnection());

        Button filterFileButton = new Button(buttonBar, SWT.PUSH);
        filterFileButton.setText("New Filter Files...");
        filterFileButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        filterFileButton.addListener(SWT.Selection, e -> onNewFilterFiles());

        Button selectButton = new Button(buttonBar, SWT.PUSH);
        selectButton.setText("Select");
        selectButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        selectButton.addListener(SWT.Selection, e -> onSelect());

        Button cancelButton = new Button(buttonBar, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        cancelButton.addListener(SWT.Selection, e -> onCancel());

        shell.setDefaultButton(selectButton);
    }

    private void onNewFilterFiles() {
        DirectoryDialog dialog = new DirectoryDialog(parentShell);
        dialog.setMessage("Select a filter files");
        dialog.setFilterPath(System.getProperty("user.home")); // Windows specific
        String file = dialog.open();
        logger.info("RESULT={}", file);
        if (file != null) {
            shell.dispose();
        }
    }

    private void onNewConnection() {
        ConnectionDialog dialog = new ConnectionDialog(parentShell, configManager);
        ConnectionConfig newConfig = dialog.open();

        if (newConfig != null) {
            selectedConnection = newConfig;
            shell.dispose();
        }
    }

    private void onSelect() {
        int index = connectionList.getSelectionIndex();

        if (index < 0) {
            MessageBox box = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
            box.setText("No Selection");
            box.setMessage("Please select a connection.");
            box.open();
            return;
        }

        selectedConnection = availableConnections.get(index);
        shell.dispose();
    }

    private void onCancel() {
        selectedConnection = null;
        shell.dispose();
    }
}
