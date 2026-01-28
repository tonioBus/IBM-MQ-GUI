package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.Configuration;
import com.aquila.ibm.mq.gui.model.MessageTemplate;
import com.aquila.ibm.mq.gui.mq.MessageService;
import com.aquila.ibm.mq.gui.util.TemplateProcessor;
import com.ibm.mq.constants.MQConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SendMessageDialog {
    private static final Logger logger = LoggerFactory.getLogger(SendMessageDialog.class);
    private final Shell parent;
    private final MessageService messageService;
    private final Configuration configuration;
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
    private Combo templateCombo;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean isSending = false;

    public SendMessageDialog(Shell parent, MessageService messageService, Configuration configuration) {
        this.parent = parent;
        this.messageService = messageService;
        this.configuration = configuration;
    }

    public void open(String queueName) {
        this.queueName = queueName;

        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
        shell.setText("Send Message to " + queueName);
        shell.setLayout(new GridLayout(1, false));
        shell.setSize(600, 600);

        createTemplateArea();
        createMessageArea();
        createOptionsArea();
        createBatchOptionsArea();
        createProgressPanel();
        createButtons();

        shell.open();
    }

    private void createTemplateArea() {
        Group templateGroup = new Group(shell, SWT.NONE);
        templateGroup.setText("Template");
        templateGroup.setLayout(new GridLayout(4, false));
        templateGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(templateGroup, SWT.NONE).setText("Template:");
        templateCombo = new Combo(templateGroup, SWT.READ_ONLY);
        templateCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refreshTemplateCombo();
        templateCombo.addListener(SWT.Selection, e -> loadSelectedTemplate());

        Button saveTemplateButton = new Button(templateGroup, SWT.PUSH);
        saveTemplateButton.setText("Save...");
        saveTemplateButton.addListener(SWT.Selection, e -> saveTemplate());

        Button deleteTemplateButton = new Button(templateGroup, SWT.PUSH);
        deleteTemplateButton.setText("Delete");
        deleteTemplateButton.addListener(SWT.Selection, e -> deleteSelectedTemplate());
    }

    private void refreshTemplateCombo() {
        templateCombo.removeAll();
        templateCombo.add("");
        Map<String, MessageTemplate> templates = configuration.loadMessageTemplates();
        for (String name : templates.keySet()) {
            templateCombo.add(name);
        }
        templateCombo.select(0);
    }

    private void loadSelectedTemplate() {
        String selected = templateCombo.getText();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        MessageTemplate template = configuration.getMessageTemplate(selected);
        if (template != null) {
            messageText.setText(template.getContent() != null ? template.getContent() : "");
            prioritySpinner.setSelection(template.getPriority());
            persistenceCombo.select(getPersistenceIndex(template.getPersistence()));
            messageCountSpinner.setSelection(template.getMessageCount() > 0 ? template.getMessageCount() : 1);
            delaySpinner.setSelection(template.getDelayMs());
        }
    }

    private int getPersistenceIndex(int persistence) {
        return switch (persistence) {
            case MQConstants.MQPER_NOT_PERSISTENT -> 0;
            case MQConstants.MQPER_PERSISTENT -> 1;
            default -> 2;
        };
    }

    private void saveTemplate() {
        InputDialog dialog = new InputDialog(shell, "Save Template", "Enter template name:", "");
        String name = dialog.open();
        if (name != null && !name.trim().isEmpty()) {
            MessageTemplate template = new MessageTemplate(
                    name.trim(),
                    messageText.getText(),
                    prioritySpinner.getSelection(),
                    getPersistenceValue(),
                    messageCountSpinner.getSelection(),
                    delaySpinner.getSelection()
            );
            configuration.saveMessageTemplate(template);
            refreshTemplateCombo();
            selectTemplate(name.trim());
            showInfo("Template saved successfully");
        }
    }

    private void selectTemplate(String name) {
        String[] items = templateCombo.getItems();
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(name)) {
                templateCombo.select(i);
                break;
            }
        }
    }

    private void deleteSelectedTemplate() {
        String selected = templateCombo.getText();
        if (selected == null || selected.isEmpty()) {
            showError("Please select a template to delete");
            return;
        }

        MessageBox confirmBox = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
        confirmBox.setText("Confirm Delete");
        confirmBox.setMessage("Delete template '" + selected + "'?");
        if (confirmBox.open() == SWT.YES) {
            configuration.deleteMessageTemplate(selected);
            refreshTemplateCombo();
            showInfo("Template deleted");
        }
    }

    private void createMessageArea() {
        Group messageGroup = new Group(shell, SWT.NONE);
        messageGroup.setText("Message Content (supports template variables)");
        messageGroup.setLayout(new GridLayout(2, false));
        messageGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        messageText = new Text(messageGroup, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        messageText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite helpPanel = new Composite(messageGroup, SWT.NONE);
        helpPanel.setLayout(new GridLayout(1, false));
        helpPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));

        Button helpButton = new Button(helpPanel, SWT.PUSH);
        helpButton.setText("?");
        helpButton.setToolTipText("Show template variables help");
        helpButton.addListener(SWT.Selection, e -> showTemplateHelp());

        Button previewButton = new Button(helpPanel, SWT.PUSH);
        previewButton.setText("Preview");
        previewButton.setToolTipText("Preview message with variables replaced");
        previewButton.addListener(SWT.Selection, e -> previewMessage());
    }

    private void showTemplateHelp() {
        Shell helpShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.RESIZE);
        helpShell.setText("Template Variables Help");
        helpShell.setLayout(new GridLayout(1, false));
        helpShell.setSize(400, 450);

        Text helpText = new Text(helpShell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.READ_ONLY);
        helpText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        helpText.setText(TemplateProcessor.getHelpText());

        Button closeBtn = new Button(helpShell, SWT.PUSH);
        closeBtn.setText("Close");
        closeBtn.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        closeBtn.addListener(SWT.Selection, e -> helpShell.close());

        helpShell.open();
    }

    private void previewMessage() {
        String content = messageText.getText();
        if (content.isEmpty()) {
            showInfo("No message content to preview");
            return;
        }

        TemplateProcessor processor = new TemplateProcessor();
        processor.setSequenceNumber(0);
        processor.setTotalCount(messageCountSpinner.getSelection());
        String preview = processor.process(content);

        Shell previewShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.RESIZE);
        previewShell.setText("Message Preview");
        previewShell.setLayout(new GridLayout(1, false));
        previewShell.setSize(500, 400);

        Text previewText = new Text(previewShell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
        previewText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        previewText.setText(preview);

        Button closeBtn = new Button(previewShell, SWT.PUSH);
        closeBtn.setText("Close");
        closeBtn.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
        closeBtn.addListener(SWT.Selection, e -> previewShell.close());

        previewShell.open();
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
            TemplateProcessor processor = new TemplateProcessor();
            processor.setSequenceNumber(0);
            processor.setTotalCount(1);
            String processedContent = processor.process(content);

            messageService.putMessage(queueName, processedContent, priority, persistence);
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
                TemplateProcessor processor = new TemplateProcessor();
                processor.setTotalCount(messageCount);

                sent = messageService.putMessages(queueName, index -> {
                    processor.setSequenceNumber(index);
                    return processor.process(content);
                }, priority, persistence, messageCount, delayMs, new MessageService.BatchCallback() {
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
        templateCombo.setEnabled(enabled);
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

    private static class InputDialog {
        private final Shell parent;
        private final String title;
        private final String message;
        private final String initialValue;
        private String result;

        public InputDialog(Shell parent, String title, String message, String initialValue) {
            this.parent = parent;
            this.title = title;
            this.message = message;
            this.initialValue = initialValue;
        }

        public String open() {
            Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
            dialog.setText(title);
            dialog.setLayout(new GridLayout(2, false));

            Label label = new Label(dialog, SWT.NONE);
            label.setText(message);
            label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

            Text text = new Text(dialog, SWT.BORDER);
            text.setText(initialValue);
            GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
            textData.widthHint = 250;
            text.setLayoutData(textData);

            Button okButton = new Button(dialog, SWT.PUSH);
            okButton.setText("OK");
            okButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
            okButton.addListener(SWT.Selection, e -> {
                result = text.getText();
                dialog.close();
            });

            Button cancelButton = new Button(dialog, SWT.PUSH);
            cancelButton.setText("Cancel");
            cancelButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
            cancelButton.addListener(SWT.Selection, e -> {
                result = null;
                dialog.close();
            });

            dialog.setDefaultButton(okButton);
            dialog.pack();
            dialog.open();

            Display display = parent.getDisplay();
            while (!dialog.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }

            return result;
        }
    }
}
