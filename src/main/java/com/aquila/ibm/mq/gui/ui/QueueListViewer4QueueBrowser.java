package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import lombok.Setter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class QueueListViewer4QueueBrowser extends Composite {

    public interface ContextMenuActionListener {
        void onSendMessage(QueueInfo queue);
        void onBrowseMessages(QueueInfo queue);
        void onRefreshQueue(QueueInfo queue);
    }
    private final Table table;
    private final List<QueueInfo> queues;
    private final List<QueueInfo> filteredQueues;
    private final AlertManager alertManager;
    private Consumer<QueueInfo> selectionListener;
    @Setter
    private ContextMenuActionListener contextMenuActionListener;
    private final Color greenColor;
    private final Color yellowColor;
    private final Color redColor;

    private final Composite progressPanel;
    private ProgressBar progressBar;
    private final Label progressLabel;

    private Text regexFilterText;
    private Spinner depthFilterSpinner;
    private Label filterStatusLabel;

    public QueueListViewer4QueueBrowser(Composite parent, int style, AlertManager alertManager) {
        super(parent, style);
        this.queues = new ArrayList<>();
        this.filteredQueues = new ArrayList<>();
        this.alertManager = alertManager;

        setLayout(new GridLayout());

        greenColor = new Color(getDisplay(), 200, 255, 200);
        yellowColor = new Color(getDisplay(), 255, 255, 200);
        redColor = new Color(getDisplay(), 255, 200, 200);

        Label label = new Label(this, SWT.NONE);
        label.setText("Queues:");

        // Create filter panel
        createFilterPanel(this);

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

    private Composite createFilterPanel(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(5, false);
        layout.marginHeight = 5;
        layout.marginWidth = 0;
        panel.setLayout(layout);
        panel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Regex filter
        Label nameLabel = new Label(panel, SWT.NONE);
        nameLabel.setText("Name:");

        regexFilterText = new Text(panel, SWT.BORDER | SWT.SEARCH);
        regexFilterText.setMessage("Filter pattern...");
        GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        textData.widthHint = 150;
        regexFilterText.setLayoutData(textData);
        regexFilterText.addListener(SWT.Modify, e -> applyFilters());

        // Depth filter
        Label depthLabel = new Label(panel, SWT.NONE);
        depthLabel.setText("Depth >=");

        depthFilterSpinner = new Spinner(panel, SWT.BORDER);
        depthFilterSpinner.setMinimum(0);
        depthFilterSpinner.setMaximum(999999);
        depthFilterSpinner.setIncrement(1);
        depthFilterSpinner.setPageIncrement(10);
        depthFilterSpinner.setSelection(0);
        depthFilterSpinner.setLayoutData(new GridData(50, SWT.DEFAULT));
        depthFilterSpinner.addListener(SWT.Selection, e -> applyFilters());

        // Status label
        filterStatusLabel = new Label(panel, SWT.NONE);
        filterStatusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return panel;
    }

    public void setQueues(List<QueueInfo> queues) {
        this.queues.clear();
        this.queues.addAll(queues);
        applyFilters();
    }

    public void refresh() {
        table.removeAll();

        for (QueueInfo queue : filteredQueues) {
            TableItem item = new TableItem(table, SWT.NONE);
            updateTableItem(item, queue);
        }

        updateFilterStatus();

        if (!filteredQueues.isEmpty() && table.getSelectionIndex() < 0) {
            table.select(0);
            if (selectionListener != null) {
                selectionListener.accept(filteredQueues.get(0));
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
        filteredQueues.clear();
        table.removeAll();
        updateFilterStatus();
    }

    public List<QueueInfo> getQueues() {
        return new ArrayList<>(queues);
    }

    public QueueInfo getSelectedQueue() {
        int index = table.getSelectionIndex();
        if (index >= 0 && index < queues.size()) {
            return queues.get(index);
        }
        return null;
    }

    public List<QueueInfo> getSelectedQueues() {
        List<QueueInfo> selectedQueues = new ArrayList<>();
        int[] selectionIndices = table.getSelectionIndices();

        for (int index : selectionIndices) {
            if (index >= 0 && index < filteredQueues.size()) {
                selectedQueues.add(filteredQueues.get(index));
            }
        }

        return selectedQueues;
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
            }
        });
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

    private void applyFilters() {
        filteredQueues.clear();

        String regexPattern = regexFilterText.getText().trim();
        int minDepth = depthFilterSpinner.getSelection();

        // Compile regex pattern
        Pattern pattern = null;
        if (!regexPattern.isEmpty()) {
            try {
                pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
                regexFilterText.setBackground(null);  // Clear error indicator
            } catch (PatternSyntaxException e) {
                // Invalid regex - show error and display all queues
                regexFilterText.setBackground(getDisplay().getSystemColor(SWT.COLOR_RED));
                filteredQueues.addAll(queues);
                refresh();
                return;
            }
        }

        // Apply filters using streams
        final Pattern finalPattern = pattern;
        filteredQueues.addAll(
            queues.stream()
                .filter(q -> finalPattern == null || finalPattern.matcher(q.getName()).find())
                .filter(q -> minDepth == 0 || q.getCurrentDepth() >= minDepth)
                .toList()
        );

        refresh();
    }

    private void updateFilterStatus() {
        if (filteredQueues.size() == queues.size()) {
            filterStatusLabel.setText(String.format("%d queues", queues.size()));
        } else {
            filterStatusLabel.setText(String.format("%d of %d queues",
                filteredQueues.size(), queues.size()));
        }
    }

    public void clearFilters() {
        regexFilterText.setText("");
        depthFilterSpinner.setSelection(0);
        // applyFilters() will be called automatically via listeners
    }
}
