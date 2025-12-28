package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.mq.MessageService;
import com.ibm.mq.constants.MQConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendMessageDialog {
    private static final Logger logger = LoggerFactory.getLogger(SendMessageDialog.class);
    private final Shell parent;
    private final MessageService messageService;
    private Shell shell;
    private String queueName;
    private Text messageText;
    private Spinner prioritySpinner;
    private Combo persistenceCombo;

    public SendMessageDialog(Shell parent, MessageService messageService) {
        this.parent = parent;
        this.messageService = messageService;
    }

    public void open(String queueName) {
        this.queueName = queueName;

        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Send Message to " + queueName);
        shell.setLayout(new GridLayout(1, false));
        shell.setSize(600, 500);

        createMessageArea();
        createOptionsArea();
        createButtons();

        shell.open();
        Display display = parent.getDisplay();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void createMessageArea() {
        Group messageGroup = new Group(shell, SWT.NONE);
        messageGroup.setText("Message Content");
        messageGroup.setLayout(new GridLayout());
        messageGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        messageText = new Text(messageGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        messageText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    private void createOptionsArea() {
        Group optionsGroup = new Group(shell, SWT.NONE);
        optionsGroup.setText("Message Options");
        optionsGroup.setLayout(new GridLayout(2, false));
        optionsGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(optionsGroup, SWT.NONE).setText("Priority:");
        prioritySpinner = new Spinner(optionsGroup, SWT.BORDER);
        prioritySpinner.setMinimum(0);
        prioritySpinner.setMaximum(9);
        prioritySpinner.setSelection(4);

        new Label(optionsGroup, SWT.NONE).setText("Persistence:");
        persistenceCombo = new Combo(optionsGroup, SWT.READ_ONLY);
        persistenceCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        persistenceCombo.add("Not Persistent");
        persistenceCombo.add("Persistent");
        persistenceCombo.add("As Queue Default");
        persistenceCombo.select(2);
    }

    private void createButtons() {
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayout(new GridLayout(2, false));
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        Button sendButton = new Button(buttonBar, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.addListener(SWT.Selection, e -> sendMessage());

        Button cancelButton = new Button(buttonBar, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.addListener(SWT.Selection, e -> shell.close());
    }

    private void sendMessage() {
        String content = messageText.getText();
        if (content.isEmpty()) {
            showError("Please enter message content");
            return;
        }

        int priority = prioritySpinner.getSelection();
        int persistence = getPersistenceValue();

        try {
            messageService.putMessage(queueName, content, priority, persistence);
            showInfo("Message sent successfully");
            shell.close();
        } catch (Exception e) {
            logger.error("Failed to send message", e);
            showError("Failed to send message: " + e.getMessage());
        }
    }

    private int getPersistenceValue() {
        switch (persistenceCombo.getSelectionIndex()) {
            case 0: return MQConstants.MQPER_NOT_PERSISTENT;
            case 1: return MQConstants.MQPER_PERSISTENT;
            default: return MQConstants.MQPER_PERSISTENCE_AS_Q_DEF;
        }
    }

    private void showError(String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        box.setText("Error");
        box.setMessage(message);
        box.open();
    }

    private void showInfo(String message) {
        MessageBox box = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        box.setText("Success");
        box.setMessage(message);
        box.open();
    }
}
