package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.mq.MessageService;
import com.ibm.mq.constants.MQConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class SendMessageDialog {
    private static final Logger logger = LoggerFactory.getLogger(SendMessageDialog.class);
    private final Shell parent;
    private final MessageService messageService;
    private Shell shell;
    private String queueName;
    private Text messageText;
    private Spinner prioritySpinner;
    private Combo persistenceCombo;
    private Spinner messageCountSpinner;
    private Spinner delaySpinner;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Composite progressPanel;
    private Button sendButton;
    private Button closeButton;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean isSending = false;

    public SendMessageDialog(Shell parent, MessageService messageService) {
        this.parent = parent;
        this.messageService = messageService;
    }

    public void open(String queueName) {
        this.queueName = queueName;

        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
        shell.setText("Send Message to " + queueName);
        shell.setLayout(new GridLayout(1, false));
        shell.setSize(600, 550);

        createMessageArea();
        createOptionsArea();
        createBatchOptionsArea();
        createProgressPanel();
        createButtons();

        shell.open();
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
        optionsGroup.setLayout(new GridLayout(4, false));
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

    private void createBatchOptionsArea() {
        Group batchGroup = new Group(shell, SWT.NONE);
        batchGroup.setText("Batch Options");
        batchGroup.setLayout(new GridLayout(4, false));
        batchGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(batchGroup, SWT.NONE).setText("Number of messages:");
        messageCountSpinner = new Spinner(batchGroup, SWT.BORDER);
        messageCountSpinner.setMinimum(1);
        messageCountSpinner.setMaximum(100000);
        messageCountSpinner.setSelection(1);

        new Label(batchGroup, SWT.NONE).setText("Delay between (ms):");
        delaySpinner = new Spinner(batchGroup, SWT.BORDER);
        delaySpinner.setMinimum(0);
        delaySpinner.setMaximum(60000);
        delaySpinner.setSelection(0);
    }

    private void createProgressPanel() {
        progressPanel = new Composite(shell, SWT.NONE);
        progressPanel.setLayout(new GridLayout(1, false));
        GridData progressData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        progressPanel.setLayoutData(progressData);

        progressBar = new ProgressBar(progressPanel, SWT.HORIZONTAL);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);

        progressLabel = new Label(progressPanel, SWT.NONE);
        progressLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        progressLabel.setText("");

        progressPanel.setVisible(false);
        ((GridData) progressPanel.getLayoutData()).exclude = true;
    }

    private void createButtons() {
        Composite buttonBar = new Composite(shell, SWT.NONE);
        buttonBar.setLayout(new GridLayout(2, false));
        buttonBar.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        sendButton = new Button(buttonBar, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.addListener(SWT.Selection, e -> handleSendButtonClick());

        closeButton = new Button(buttonBar, SWT.PUSH);
        closeButton.setText("Close");
        closeButton.addListener(SWT.Selection, e -> shell.close());
    }

    private void handleSendButtonClick() {
        if (isSending) {
            cancelled.set(true);
        } else {
            sendMessage();
        }
    }

    private void sendMessage() {
        String content = messageText.getText();
        if (content.isEmpty()) {
            showError("Please enter message content");
            return;
        }

        int priority = prioritySpinner.getSelection();
        int persistence = getPersistenceValue();
        int messageCount = messageCountSpinner.getSelection();
        int delayMs = delaySpinner.getSelection();

        if (messageCount == 1) {
            sendSingleMessage(content, priority, persistence);
        } else {
            sendBatchMessages(content, priority, persistence, messageCount, delayMs);
        }
    }

    private void sendSingleMessage(String content, int priority, int persistence) {
        try {
            messageService.putMessage(queueName, content, priority, persistence);
            showInfo("Message sent successfully");
            shell.close();
        } catch (Exception e) {
            logger.error("Failed to send message", e);
            showError("Failed to send message: " + e.getMessage());
        }
    }

    private void sendBatchMessages(String content, int priority, int persistence, int messageCount, int delayMs) {
        isSending = true;
        cancelled.set(false);

        setControlsEnabled(false);
        sendButton.setText("Cancel");
        sendButton.setEnabled(true);

        showProgress(true);
        progressBar.setSelection(0);
        progressLabel.setText("Sent 0 of " + messageCount + " messages...");

        new Thread(() -> {
            int sent = 0;
            Exception error = null;

            try {
                sent = messageService.putMessages(queueName, content, priority, persistence,
                        messageCount, delayMs, new MessageService.BatchCallback() {
                            @Override
                            public boolean isCancelled() {
                                return cancelled.get();
                            }

                            @Override
                            public void onProgress(int sentCount, int total) {
                                Display display = shell.getDisplay();
                                if (!shell.isDisposed()) {
                                    display.asyncExec(() -> {
                                        if (!shell.isDisposed()) {
                                            int percent = (sentCount * 100) / total;
                                            progressBar.setSelection(percent);
                                            progressLabel.setText("Sent " + sentCount + " of " + total + " messages...");
                                        }
                                    });
                                }
                            }
                        });
            } catch (Exception e) {
                error = e;
                logger.error("Failed to send batch messages", e);
            }

            final int finalSent = sent;
            final Exception finalError = error;
            final boolean wasCancelled = cancelled.get();

            Display display = shell.getDisplay();
            if (!shell.isDisposed()) {
                display.asyncExec(() -> {
                    if (!shell.isDisposed()) {
                        isSending = false;
                        setControlsEnabled(true);
                        sendButton.setText("Send");
                        showProgress(false);

                        if (finalError != null) {
                            showError("Failed to send messages.\nSent " + finalSent + " of " + messageCount + " messages.\nError: " + finalError.getMessage());
                        } else if (wasCancelled) {
                            showInfo("Batch sending cancelled.\nSent " + finalSent + " of " + messageCount + " messages.");
                        } else {
                            showInfo("Successfully sent " + finalSent + " messages.");
                            shell.close();
                        }
                    }
                });
            }
        }).start();
    }

    private void setControlsEnabled(boolean enabled) {
        messageText.setEnabled(enabled);
        prioritySpinner.setEnabled(enabled);
        persistenceCombo.setEnabled(enabled);
        messageCountSpinner.setEnabled(enabled);
        delaySpinner.setEnabled(enabled);
        closeButton.setEnabled(enabled);
    }

    private void showProgress(boolean visible) {
        progressPanel.setVisible(visible);
        ((GridData) progressPanel.getLayoutData()).exclude = !visible;
        shell.layout(true, true);
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
