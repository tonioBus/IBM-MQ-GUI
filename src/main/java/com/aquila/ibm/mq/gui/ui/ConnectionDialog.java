package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.ConfigManager;
import com.aquila.ibm.mq.gui.model.ConnectionConfig;
import com.aquila.ibm.mq.gui.mq.MQConnectionManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ConnectionDialog {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionDialog.class);
    private final Shell parent;
    private final ConfigManager configManager;
    private Shell shell;
    private ConnectionConfig result;

    private Combo profileCombo;
    private Text nameText;
    private Text hostText;
    private Text portText;
    private Text channelText;
    private Text queueManagerText;
    private Text usernameText;
    private Text passwordText;
    private Button saveProfileButton;

    public ConnectionDialog(Shell parent, ConfigManager configManager) {
        this.parent = parent;
        this.configManager = configManager;
    }

    public ConnectionConfig open() {
        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText("Connect to Queue Manager");
        shell.setLayout(new GridLayout(1, false));
        shell.setSize(500, 500);

        createProfileSection();
        createConnectionFields();
        createButtons();

        loadProfiles();

        shell.open();
        Display display = parent.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        return result;
    }

    private void createProfileSection() {
        Group profileGroup = new Group(shell, SWT.NONE);
        profileGroup.setText("Connection Profile");
        profileGroup.setLayout(new GridLayout(3, false));
        profileGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(profileGroup, SWT.NONE).setText("Profile:");

        profileCombo = new Combo(profileGroup, SWT.READ_ONLY);
        profileCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        profileCombo.addListener(SWT.Selection, e -> loadSelectedProfile());

        Button deleteButton = new Button(profileGroup, SWT.PUSH);
        deleteButton.setText("Delete");
        deleteButton.addListener(SWT.Selection, e -> deleteProfile());
    }

    private void createConnectionFields() {
        Group connGroup = new Group(shell, SWT.NONE);
        connGroup.setText("Connection Details");
        connGroup.setLayout(new GridLayout(2, false));
        connGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        new Label(connGroup, SWT.NONE).setText("Profile Name:");
        nameText = new Text(connGroup, SWT.BORDER);
        nameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(connGroup, SWT.NONE).setText("Host:");
        hostText = new Text(connGroup, SWT.BORDER);
        hostText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        hostText.setText("localhost");

        new Label(connGroup, SWT.NONE).setText("Port:");
        portText = new Text(connGroup, SWT.BORDER);
        portText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        portText.setText("1414");

        new Label(connGroup, SWT.NONE).setText("Channel:");
        channelText = new Text(connGroup, SWT.BORDER);
        channelText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        channelText.setText("DEV.APP.SVRCONN");

        new Label(connGroup, SWT.NONE).setText("Queue Manager:");
        queueManagerText = new Text(connGroup, SWT.BORDER);
        queueManagerText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        queueManagerText.setText("QM1");

        new Label(connGroup, SWT.NONE).setText("Username:");
        usernameText = new Text(connGroup, SWT.BORDER);
        usernameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(connGroup, SWT.NONE).setText("Password:");
        passwordText = new Text(connGroup, SWT.BORDER | SWT.PASSWORD);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createButtons() {
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayout(new GridLayout(4, false));
        buttonBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        saveProfileButton = new Button(buttonBar, SWT.PUSH);
        saveProfileButton.setText("Save Profile");
        saveProfileButton.addListener(SWT.Selection, e -> saveProfile());

        Button testButton = new Button(buttonBar, SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.addListener(SWT.Selection, e -> testConnection());

        Button connectButton = new Button(buttonBar, SWT.PUSH);
        connectButton.setText("Create");
        connectButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        connectButton.addListener(SWT.Selection, e -> create());
        shell.setDefaultButton(connectButton);

        Button cancelButton = new Button(buttonBar, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.addListener(SWT.Selection, e -> {
            result = null;
            shell.close();
        });
    }

    private void loadProfiles() {
        List<ConnectionConfig> connections = configManager.loadConnections();
        profileCombo.removeAll();
        profileCombo.add("-- New Connection --");
        for (ConnectionConfig config : connections) {
            profileCombo.add(config.getName());
        }
        profileCombo.select(0);
    }

    private void loadSelectedProfile() {
        int index = profileCombo.getSelectionIndex();
        if (index <= 0) {
            clearFields();
            return;
        }

        String profileName = profileCombo.getItem(index);
        List<ConnectionConfig> connections = configManager.loadConnections();

        for (ConnectionConfig config : connections) {
            if (config.getName().equals(profileName)) {
                nameText.setText(config.getName());
                hostText.setText(config.getHost());
                portText.setText(String.valueOf(config.getPort()));
                channelText.setText(config.getChannel());
                queueManagerText.setText(config.getQueueManager());
                usernameText.setText(config.getUsername() != null ? config.getUsername() : "");
                passwordText.setText(config.getPassword() != null ? config.getPassword() : "");
                break;
            }
        }
    }

    private void clearFields() {
        nameText.setText("");
        hostText.setText("localhost");
        portText.setText("1414");
        channelText.setText("DEV.APP.SVRCONN");
        queueManagerText.setText("QM1");
        usernameText.setText("");
        passwordText.setText("");
    }

    private void saveProfile() {
        final ConnectionConfig config = getConnectionConfig();
        if (config.getName() == null || config.getName().isEmpty()) {
            showError("Please enter a profile name");
            return;
        }

        configManager.saveConnection(config);
        loadProfiles();

        for (int i = 0; i < profileCombo.getItemCount(); i++) {
            if (profileCombo.getItem(i).equals(config.getName())) {
                profileCombo.select(i);
                break;
            }
        }

        showInfo("Profile saved successfully");
    }

    private void deleteProfile() {
        int index = profileCombo.getSelectionIndex();
        if (index <= 0) {
            return;
        }

        String profileName = profileCombo.getItem(index);
        MessageBox confirm = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirm.setText("Confirm Delete");
        confirm.setMessage("Delete profile '" + profileName + "'?");

        if (confirm.open() == SWT.YES) {
            configManager.deleteConnection(profileName);
            loadProfiles();
            clearFields();
        }
    }

    private void testConnection() {
        ConnectionConfig config = getConnectionConfig();
        MQConnectionManager testManager = new MQConnectionManager();

        try {
            testManager.testConnection(config);
            showInfo("Connection test successful!");
        } catch (Exception e) {
            logger.error("Connection test failed", e);
            showError("Connection test failed: " + e.getMessage());
        } finally {
            testManager.disconnect();
        }
    }

    private void create() {
        ConnectionConfig config = getConnectionConfig();
        result = config;
        shell.close();
    }

    private ConnectionConfig getConnectionConfig() {
        ConnectionConfig config = new ConnectionConfig();
        config.setName(nameText.getText().trim());
        config.setHost(hostText.getText().trim());

        try {
            config.setPort(Integer.parseInt(portText.getText().trim()));
        } catch (NumberFormatException e) {
            config.setPort(1414);
        }

        config.setChannel(channelText.getText().trim());
        config.setQueueManager(queueManagerText.getText().trim());
        config.setUsername(usernameText.getText().trim());
        config.setPassword(passwordText.getText());

        return config;
    }

    private void showError(String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        box.setText("Error");
        box.setMessage(message);
        box.open();
    }

    private void showInfo(String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText("Information");
        box.setMessage(message);
        box.open();
    }
}
