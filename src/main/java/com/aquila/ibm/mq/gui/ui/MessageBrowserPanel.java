package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.model.MessageInfo;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.mq.MessageService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MessageBrowserPanel extends Composite {
    private static final Logger logger = LoggerFactory.getLogger(MessageBrowserPanel.class);
    private final MessageService messageService;
    private Table messagesTable;
    private Text messageDetailText;
    private QueueInfo currentQueue;
    private List<MessageInfo> messages;

    public MessageBrowserPanel(Composite parent, int style, MessageService messageService) {
        super(parent, style);
        this.messageService = messageService;

        setLayout(new GridLayout());

        createToolbar();

        SashForm sashForm = new SashForm(this, SWT.VERTICAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createMessagesTable(sashForm);
        createMessageDetailArea(sashForm);

        sashForm.setWeights(new int[]{60, 40});
    }

    private void createToolbar() {
        Composite toolbar = new Composite(this, SWT.NONE);
        toolbar.setLayout(new GridLayout(3, false));
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Button refreshButton = new Button(toolbar, SWT.PUSH);
        refreshButton.setText("Refresh");
        refreshButton.addListener(SWT.Selection, e -> browseMessages());

        Button clearButton = new Button(toolbar, SWT.PUSH);
        clearButton.setText("Clear");
        clearButton.addListener(SWT.Selection, e -> clear());

        Label statusLabel = new Label(toolbar, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createMessagesTable(Composite parent) {
        Composite tableComp = new Composite(parent, SWT.NONE);
        tableComp.setLayout(new GridLayout());

        Label label = new Label(tableComp, SWT.NONE);
        label.setText("Messages:");

        messagesTable = new Table(tableComp, SWT.BORDER | SWT.FULL_SELECTION);
        messagesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        messagesTable.setHeaderVisible(true);
        messagesTable.setLinesVisible(true);

        TableColumn msgIdColumn = new TableColumn(messagesTable, SWT.LEFT);
        msgIdColumn.setText("Message ID");
        msgIdColumn.setWidth(200);

        TableColumn sizeColumn = new TableColumn(messagesTable, SWT.RIGHT);
        sizeColumn.setText("Size");
        sizeColumn.setWidth(80);

        TableColumn priorityColumn = new TableColumn(messagesTable, SWT.RIGHT);
        priorityColumn.setText("Priority");
        priorityColumn.setWidth(70);

        TableColumn persistenceColumn = new TableColumn(messagesTable, SWT.LEFT);
        persistenceColumn.setText("Persistence");
        persistenceColumn.setWidth(100);

        TableColumn previewColumn = new TableColumn(messagesTable, SWT.LEFT);
        previewColumn.setText("Preview");
        previewColumn.setWidth(300);

        messagesTable.addListener(SWT.Selection, e -> showMessageDetail());
    }

    private void createMessageDetailArea(Composite parent) {
        Composite detailComp = new Composite(parent, SWT.NONE);
        detailComp.setLayout(new GridLayout());

        Label label = new Label(detailComp, SWT.NONE);
        label.setText("Message Content:");

        messageDetailText = new Text(detailComp, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        messageDetailText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        messageDetailText.setEditable(false);
    }

    public void setQueue(QueueInfo queue) {
        this.currentQueue = queue;
        clear();
    }

    private void browseMessages() {
        if (currentQueue == null) {
            return;
        }

        try {
            messages = messageService.browseMessages(currentQueue.getName());
            refreshTable();
        } catch (Exception e) {
            logger.error("Failed to browse messages", e);
            showError("Failed to browse messages: " + e.getMessage());
        }
    }

    private void refreshTable() {
        messagesTable.removeAll();

        if (messages == null) {
            return;
        }

        for (MessageInfo message : messages) {
            TableItem item = new TableItem(messagesTable, SWT.NONE);
            item.setText(0, message.getMessageIdAsHex());
            item.setText(1, String.valueOf(message.getMessageLength()));
            item.setText(2, String.valueOf(message.getPriority()));
            item.setText(3, getPersistenceName(message.getPersistence()));

            String preview = message.getMessageData();
            if (preview != null && preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            item.setText(4, preview != null ? preview : "");
            item.setData(message);
        }
    }

    private void showMessageDetail() {
        int index = messagesTable.getSelectionIndex();
        if (index < 0 || messages == null || index >= messages.size()) {
            messageDetailText.setText("");
            return;
        }

        MessageInfo message = messages.get(index);
        StringBuilder detail = new StringBuilder();
        detail.append("Message ID: ").append(message.getMessageIdAsHex()).append("\n");
        detail.append("Correlation ID: ").append(message.getCorrelationIdAsHex()).append("\n");
        detail.append("Size: ").append(message.getMessageLength()).append(" bytes\n");
        detail.append("Priority: ").append(message.getPriority()).append("\n");
        detail.append("Persistence: ").append(getPersistenceName(message.getPersistence())).append("\n");
        detail.append("Format: ").append(message.getFormat()).append("\n");
        detail.append("Encoding: ").append(message.getEncoding()).append("\n");
        detail.append("Character Set: ").append(message.getCharacterSet()).append("\n");
        detail.append("\n--- Message Content ---\n\n");
        detail.append(message.getMessageData());

        messageDetailText.setText(detail.toString());
    }

    private void clear() {
        messagesTable.removeAll();
        messageDetailText.setText("");
        messages = null;
    }

    private String getPersistenceName(int persistence) {
        switch (persistence) {
            case 0: return "Not Persistent";
            case 1: return "Persistent";
            case 2: return "As Queue Default";
            default: return "Unknown (" + persistence + ")";
        }
    }

    private void showError(String message) {
        MessageBox box = new MessageBox(getShell(), SWT.ICON_ERROR | SWT.OK);
        box.setText("Error");
        box.setMessage(message);
        box.open();
    }
}
