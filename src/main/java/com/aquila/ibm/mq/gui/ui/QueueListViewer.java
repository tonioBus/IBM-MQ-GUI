package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class QueueListViewer extends Composite {

    public interface ContextMenuActionListener {
        void onSendMessage(QueueInfo queue);
        void onBrowseMessages(QueueInfo queue);
        void onRefreshQueue(QueueInfo queue);
        void onCopyQueueName(QueueInfo queue);
    }
    private final Table table;
    private final List<QueueInfo> queues;
    private final AlertManager alertManager;
    private Consumer<QueueInfo> selectionListener;
    private ContextMenuActionListener contextMenuActionListener;
    private Color greenColor;
    private Color yellowColor;
    private Color redColor;

    private Composite progressPanel;
    private ProgressBar progressBar;
    private Label progressLabel;

    public QueueListViewer(Composite parent, int style, AlertManager alertManager) {
        super(parent, style);
        this.queues = new ArrayList<>();
        this.alertManager = alertManager;

        setLayout(new GridLayout());

        greenColor = new Color(getDisplay(), 200, 255, 200);
        yellowColor = new Color(getDisplay(), 255, 255, 200);
        redColor = new Color(getDisplay(), 255, 200, 200);

        Label label = new Label(this, SWT.NONE);
        label.setText("Queues:");

        // Create progress panel (hidden by default)
        progressPanel = new Composite(this, SWT.NONE);
        GridLayout progressLayout = new GridLayout(1, false);
        progressLayout.marginHeight = 5;
        progressLayout.marginWidth = 0;
        progressPanel.setLayout(progressLayout);
        GridData progressPanelData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        progressPanelData.exclude = true; // Hidden by default
        progressPanel.setLayoutData(progressPanelData);
        progressPanel.setVisible(false);

        progressBar = new ProgressBar(progressPanel, SWT.INDETERMINATE);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        progressLabel = new Label(progressPanel, SWT.NONE);
        progressLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        TableColumn nameColumn = new TableColumn(table, SWT.LEFT);
        nameColumn.setText("Queue Name");
        nameColumn.setWidth(250);

        TableColumn depthColumn = new TableColumn(table, SWT.RIGHT);
        depthColumn.setText("Depth");
        depthColumn.setWidth(80);

        TableColumn maxDepthColumn = new TableColumn(table, SWT.RIGHT);
        maxDepthColumn.setText("Max Depth");
        maxDepthColumn.setWidth(80);

        TableColumn percentColumn = new TableColumn(table, SWT.RIGHT);
        percentColumn.setText("% Full");
        percentColumn.setWidth(70);

        table.addListener(SWT.Selection, e -> {
            int index = table.getSelectionIndex();
            if (index >= 0 && index < queues.size() && selectionListener != null) {
                selectionListener.accept(queues.get(index));
            }
        });

        createContextMenu();

        addDisposeListener(e -> {
            greenColor.dispose();
            yellowColor.dispose();
            redColor.dispose();
        });
    }

    public void setQueues(List<QueueInfo> queues) {
        this.queues.clear();
        this.queues.addAll(queues);
        refresh();
    }

    public void refresh() {
        table.removeAll();

        for (QueueInfo queue : queues) {
            TableItem item = new TableItem(table, SWT.NONE);
            updateTableItem(item, queue);
        }

        if (!queues.isEmpty() && table.getSelectionIndex() < 0) {
            table.select(0);
            if (selectionListener != null) {
                selectionListener.accept(queues.get(0));
            }
        }
    }

    private void updateTableItem(TableItem item, QueueInfo queue) {
        item.setText(0, queue.getName());
        item.setText(1, String.valueOf(queue.getCurrentDepth()));
        item.setText(2, String.valueOf(queue.getMaxDepth()));
        item.setText(3, String.format("%.1f%%", queue.getDepthPercentage()));

        ThresholdConfig.AlertLevel alertLevel = alertManager.getCurrentAlertLevel(queue.getName());

        switch (alertLevel) {
            case CRITICAL:
                item.setBackground(redColor);
                break;
            case WARNING:
                item.setBackground(yellowColor);
                break;
            default:
                if (queue.getCurrentDepth() == 0) {
                    item.setBackground(null);
                } else {
                    item.setBackground(greenColor);
                }
                break;
        }
    }

    public void clearQueues() {
        queues.clear();
        table.removeAll();
    }

    public List<QueueInfo> getQueues() {
        return new ArrayList<>(queues);
    }

    public void addSelectionListener(Consumer<QueueInfo> listener) {
        this.selectionListener = listener;
    }

    public void setContextMenuActionListener(ContextMenuActionListener listener) {
        this.contextMenuActionListener = listener;
    }

    public QueueInfo getSelectedQueue() {
        int index = table.getSelectionIndex();
        if (index >= 0 && index < queues.size()) {
            return queues.get(index);
        }
        return null;
    }

    private void createContextMenu() {
        Menu menu = new Menu(table);
        table.setMenu(menu);

        // Dynamically build menu based on selection
        menu.addListener(SWT.Show, e -> {
            // Clear existing items
            for (MenuItem item : menu.getItems()) {
                item.dispose();
            }

            // Get selected queue
            QueueInfo selectedQueue = getSelectedQueue();

            if (selectedQueue != null && contextMenuActionListener != null) {
                // Send Message action
                MenuItem sendMessageItem = new MenuItem(menu, SWT.PUSH);
                sendMessageItem.setText("Send Message...");
                sendMessageItem.addListener(SWT.Selection, ev ->
                    contextMenuActionListener.onSendMessage(selectedQueue));

                // Browse Messages action
                MenuItem browseMessagesItem = new MenuItem(menu, SWT.PUSH);
                browseMessagesItem.setText("Browse Messages...");
                browseMessagesItem.addListener(SWT.Selection, ev ->
                    contextMenuActionListener.onBrowseMessages(selectedQueue));

                // Separator
                new MenuItem(menu, SWT.SEPARATOR);

                // Refresh Queue Info action
                MenuItem refreshItem = new MenuItem(menu, SWT.PUSH);
                refreshItem.setText("Refresh Queue Info");
                refreshItem.addListener(SWT.Selection, ev ->
                    contextMenuActionListener.onRefreshQueue(selectedQueue));

                // Separator
                new MenuItem(menu, SWT.SEPARATOR);

                // Copy Queue Name action
                MenuItem copyNameItem = new MenuItem(menu, SWT.PUSH);
                copyNameItem.setText("Copy Queue Name");
                copyNameItem.addListener(SWT.Selection, ev ->
                    contextMenuActionListener.onCopyQueueName(selectedQueue));
            }
        });
    }

    public void refreshQueue(QueueInfo queue) {
        // Find the queue in the list and update its table item
        for (int i = 0; i < queues.size(); i++) {
            if (queues.get(i).getName().equals(queue.getName())) {
                // Update the queue object
                queues.set(i, queue);

                // Update the corresponding table item
                if (i < table.getItemCount()) {
                    TableItem item = table.getItem(i);
                    updateTableItem(item, queue);
                }
                break;
            }
        }
    }

    public void showProgress(String message) {
        GridData progressPanelData = (GridData) progressPanel.getLayoutData();
        progressPanelData.exclude = false;
        progressPanel.setVisible(true);
        progressLabel.setText(message);
        layout(true);
    }

    public void hideProgress() {
        GridData progressPanelData = (GridData) progressPanel.getLayoutData();
        progressPanelData.exclude = true;
        progressPanel.setVisible(false);
        progressLabel.setText("");
        layout(true);
    }

    public void updateProgress(String message) {
        progressLabel.setText(message);
    }
}
