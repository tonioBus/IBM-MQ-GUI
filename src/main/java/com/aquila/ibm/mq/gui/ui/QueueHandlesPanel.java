/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * SWT panel for displaying queue handles (active connections).
 * Shows processes/applications currently using queues with details
 * like process ID, user ID, and open options.
 */
package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.model.QueueHandle;
import com.aquila.ibm.mq.gui.mq.QueueService;
import com.ibm.mq.headers.pcf.PCFException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.List;

@Slf4j
public class QueueHandlesPanel extends Composite {
    private final QueueService queueService;
    private Table handlesTable;
    private String currentQueueName;
    private Label statusLabel;
    private Button refreshButton;
    private Button showAllButton;
    private boolean showAllQueues = false;

    public QueueHandlesPanel(Composite parent, int style, QueueService queueService) {
        super(parent, style);
        this.queueService = queueService;
        setLayout(new GridLayout());

        createToolbar();
        createTable();
        createStatusBar();
    }

    private void createToolbar() {
        Composite toolbar = new Composite(this, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        toolbar.setLayout(new GridLayout(3, false));

        Label label = new Label(toolbar, SWT.NONE);
        label.setText("Processes using queues:");

        refreshButton = new Button(toolbar, SWT.PUSH);
        refreshButton.setText("Refresh");
        refreshButton.addListener(SWT.Selection, e -> refresh());

        showAllButton = new Button(toolbar, SWT.CHECK);
        showAllButton.setText("Show all queues");
        showAllButton.addListener(SWT.Selection, e -> {
            showAllQueues = showAllButton.getSelection();
            refresh();
        });
    }

    private void createTable() {
        handlesTable = new Table(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        handlesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        handlesTable.setHeaderVisible(true);
        handlesTable.setLinesVisible(true);

        TableColumn queueColumn = new TableColumn(handlesTable, SWT.LEFT);
        queueColumn.setText("Queue");
        queueColumn.setWidth(200);

        TableColumn appColumn = new TableColumn(handlesTable, SWT.LEFT);
        appColumn.setText("Application");
        appColumn.setWidth(200);

        TableColumn pidColumn = new TableColumn(handlesTable, SWT.RIGHT);
        pidColumn.setText("PID");
        pidColumn.setWidth(80);

        TableColumn tidColumn = new TableColumn(handlesTable, SWT.RIGHT);
        tidColumn.setText("TID");
        tidColumn.setWidth(80);

        TableColumn userColumn = new TableColumn(handlesTable, SWT.LEFT);
        userColumn.setText("User");
        userColumn.setWidth(120);

        TableColumn channelColumn = new TableColumn(handlesTable, SWT.LEFT);
        channelColumn.setText("Channel");
        channelColumn.setWidth(150);

        TableColumn optionsColumn = new TableColumn(handlesTable, SWT.LEFT);
        optionsColumn.setText("Open Options");
        optionsColumn.setWidth(150);
    }

    private void createStatusBar() {
        statusLabel = new Label(this, SWT.NONE);
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        statusLabel.setText("Select a queue to view handles");
    }

    public void setQueue(String queueName) {
        this.currentQueueName = queueName;
        this.showAllQueues = false;
        this.showAllButton.setSelection(false);
        refresh();
    }

    public void refresh() {
        handlesTable.removeAll();

        if (queueService == null) {
            statusLabel.setText("Queue service not available");
            return;
        }

        String queryQueueName = showAllQueues ? "*" : currentQueueName;

        if (queryQueueName == null || queryQueueName.isEmpty()) {
            statusLabel.setText("Select a queue to view handles");
            return;
        }

        statusLabel.setText("Loading handles...");

        // Run in background thread to avoid blocking UI
        Display display = getDisplay();
        new Thread(() -> {
            try {
                List<QueueHandle> handles = queueService.getQueueHandles(queryQueueName);

                display.asyncExec(() -> {
                    if (isDisposed()) return;

                    handlesTable.removeAll();

                    for (QueueHandle handle : handles) {
                        TableItem item = new TableItem(handlesTable, SWT.NONE);
                        item.setText(0, handle.getQueueName() != null ? handle.getQueueName() : "");
                        item.setText(1, handle.getApplicationName() != null ? handle.getApplicationName() : "");
                        item.setText(2, String.valueOf(handle.getProcessId()));
                        item.setText(3, String.valueOf(handle.getThreadId()));
                        item.setText(4, handle.getUserId() != null ? handle.getUserId() : "");
                        item.setText(5, handle.getChannelName() != null ? handle.getChannelName() : "");
                        item.setText(6, handle.getOpenOptionsDescription());
                    }

                    if (handles.isEmpty()) {
                        statusLabel.setText("No handles found for " + queryQueueName);
                    } else {
                        statusLabel.setText(handles.size() + " handle(s) found");
                    }
                });
            } catch (PCFException e) {
                if (!(e.completionCode == 2 && e.getReason() == 4091)) {
                    log.error("Failed to retrieve queue handles", e);
                    display.asyncExec(() -> {
                        if (isDisposed()) return;
                        statusLabel.setText("Error: " + e.getMessage());
                    });
                }
            } catch (Exception e) {
                log.error("Failed to retrieve queue handles", e);
                display.asyncExec(() -> {
                    if (isDisposed()) return;
                    statusLabel.setText("Error: " + e.getMessage());
                });
            }
        }).start();
    }
}
