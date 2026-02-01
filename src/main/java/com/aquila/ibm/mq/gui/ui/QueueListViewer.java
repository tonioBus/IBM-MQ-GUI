/*
 * IBM MQ GUI - Desktop application for IBM MQ Browsing
 *
 * Copyright (c) 2026 Anthony Bussani
 * GitHub: https://github.com/tonioBus
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 *
 * SWT table viewer for displaying queue list.
 * Features sorting, filtering, color-coded alert levels,
 * context menus, and progress indication during refresh.
 */
package com.aquila.ibm.mq.gui.ui;

import com.aquila.ibm.mq.gui.config.AlertManager;
import com.aquila.ibm.mq.gui.model.QueueInfo;
import com.aquila.ibm.mq.gui.model.ThresholdConfig;
import com.aquila.ibm.mq.gui.util.QueueNameRegexCalculator;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
public class QueueListViewer extends Composite {

    public interface ContextMenuActionListener {
        void onSendMessage(QueueInfo queue);

        void onBrowseMessages(QueueInfo queue);

        void onRefreshQueue(QueueInfo queue);

        void onCopyQueueName(QueueInfo queue);
    }

    public interface AutoRefreshListener {
        void onAutoRefreshToggled(boolean enabled);

        void onRefreshIntervalChanged(int intervalMs);
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

    // Sorting state
    private int sortColumn = 0;
    private boolean sortAscending = true;

    private final Composite progressPanel;
    private final ProgressBar progressBar;
    private final Label progressLabel;

    private Text regexFilterText;
    private Spinner depthFilterSpinner;
    private Label filterStatusLabel;

    // Auto-refresh controls
    private Button autoRefreshButton;
    private Combo refreshIntervalCombo;
    private boolean autoRefreshEnabled = false;
    @Setter
    private AutoRefreshListener autoRefreshListener;

    public QueueListViewer(Composite parent, int style, AlertManager alertManager) {
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

        table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        TableColumn labelColumn = new TableColumn(table, SWT.LEFT);
        labelColumn.setText("Label");
        labelColumn.setWidth(150);
        labelColumn.addListener(SWT.Selection, e -> sortBy(0));

        TableColumn nameColumn = new TableColumn(table, SWT.LEFT);
        nameColumn.setText("Queue Name");
        nameColumn.setWidth(250);
        nameColumn.addListener(SWT.Selection, e -> sortBy(1));

        TableColumn depthColumn = new TableColumn(table, SWT.RIGHT);
        depthColumn.setText("Depth");
        depthColumn.setWidth(80);
        depthColumn.addListener(SWT.Selection, e -> sortBy(2));

        TableColumn maxDepthColumn = new TableColumn(table, SWT.RIGHT);
        maxDepthColumn.setText("Max Depth");
        maxDepthColumn.setWidth(80);
        maxDepthColumn.addListener(SWT.Selection, e -> sortBy(3));

        TableColumn percentColumn = new TableColumn(table, SWT.RIGHT);
        percentColumn.setText("% Full");
        percentColumn.setWidth(70);
        percentColumn.addListener(SWT.Selection, e -> sortBy(4));

        table.addListener(SWT.Selection, e -> {
            int index = table.getSelectionIndex();
            if (index >= 0 && index < filteredQueues.size() && selectionListener != null) {
                selectionListener.accept(filteredQueues.get(index));
            }
        });

        createContextMenu();

        createRefreshPanel(this);

        // Create progress panel at bottom (hidden by default)
        progressPanel = new Composite(this, SWT.NONE);
        GridLayout progressLayout = new GridLayout(1, false);
        progressLayout.marginHeight = 5;
        progressLayout.marginWidth = 0;
        progressPanel.setLayout(progressLayout);
        GridData progressPanelData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        // progressPanelData.exclude = true; // Hidden by default
        progressPanel.setLayoutData(progressPanelData);
        progressPanel.setVisible(false);

        progressBar = new ProgressBar(progressPanel, SWT.INDETERMINATE);
        progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        progressLabel = new Label(progressPanel, SWT.NONE);
        progressLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        addDisposeListener(e -> {
            greenColor.dispose();
            yellowColor.dispose();
            redColor.dispose();
        });
    }

    private void createFilterPanel(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(9, false);
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

    }

    void createRefreshPanel(Composite parent) {
        Composite panel = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(6, false);
        layout.marginHeight = 2;
        layout.marginWidth = 2;
        panel.setLayout(layout);
        panel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        // Refresh Now button
        autoRefreshButton = new Button(panel, SWT.PUSH);
        autoRefreshButton.setText("Refresh Now");
        autoRefreshButton.setToolTipText("Refresh immediately queues information");
        autoRefreshButton.addListener(SWT.Selection, e -> {
            MainWindow.getInstance().refreshQueueList();
        });

        Label verticalSeparator = new Label(panel, SWT.SEPARATOR | SWT.VERTICAL);

        // Refresh interval combo
        Label refreshLabel = new Label(panel, SWT.NONE);
        refreshLabel.setText("Refresh:");

        refreshIntervalCombo = new Combo(panel, SWT.READ_ONLY);
        refreshIntervalCombo.add("1s");
        refreshIntervalCombo.add("5s");
        refreshIntervalCombo.add("10s");
        refreshIntervalCombo.add("30s");
        refreshIntervalCombo.add("1min");
        refreshIntervalCombo.add("5min");
        refreshIntervalCombo.select(1);
        refreshIntervalCombo.addListener(SWT.Selection, e -> {
            if (autoRefreshListener != null) {
                autoRefreshListener.onRefreshIntervalChanged(getSelectedRefreshInterval());
            }
        });

        // Auto-refresh button
        autoRefreshButton = new Button(panel, SWT.TOGGLE);
        autoRefreshButton.setText("Auto OFF");
        autoRefreshButton.setToolTipText("Toggle automatic refresh of queues information");
        autoRefreshButton.addListener(SWT.Selection, e -> {
            autoRefreshEnabled = autoRefreshButton.getSelection();
            updateAutoRefreshButtonState();
            if (autoRefreshListener != null) {
                autoRefreshListener.onAutoRefreshToggled(autoRefreshEnabled);
            }
        });
    }

    private void updateAutoRefreshButtonState() {
        if (autoRefreshEnabled) {
            autoRefreshButton.setText("Auto ON ");
        } else {
            autoRefreshButton.setText("Auto OFF");
        }
    }

    public int getSelectedRefreshInterval() {
        int index = refreshIntervalCombo.getSelectionIndex();
        return switch (index) {
            case 0 -> 1000;
            case 1 -> 5000;
            case 2 -> 10000;
            case 3 -> 30000;
            case 4 -> 60000;
            case 5 -> 300000;
            default -> 10000;
        };
    }

    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled;
    }

    public void setAutoRefreshEnabled(boolean enabled) {
        this.autoRefreshEnabled = enabled;
        autoRefreshButton.setSelection(enabled);
        updateAutoRefreshButtonState();
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
            if (table.getSelectionIndex() == -1) {
                log.info("Table Selection");
                table.select(0);
            }
            if (selectionListener != null) {
                selectionListener.accept(filteredQueues.get(0));
            }
        }
    }

    private void updateTableItem(TableItem item, QueueInfo queue) {
        item.setText(0, queue.getLabel() != null ? queue.getLabel() : queue.getQueue());
        item.setText(1, queue.getQueue());
        item.setText(2, String.valueOf(queue.getCurrentDepth()));
        item.setText(3, String.valueOf(queue.getMaxDepth()));
        item.setText(4, String.format("%.1f%%", queue.getDepthPercentage()));

        ThresholdConfig.AlertLevel alertLevel = alertManager.getCurrentAlertLevel(queue.getQueue());

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

    public void addSelectionListener(Consumer<QueueInfo> listener) {
        this.selectionListener = listener;
    }

    public QueueInfo getSelectedQueue() {
        final Optional<TableItem> tableItem = Arrays.stream(table.getSelection()).findFirst();
        if(tableItem.isEmpty()) {
            return null;
        }
        final String queue = tableItem.get().getText(1);
        final Optional<QueueInfo> queueInfoOpt = queues.parallelStream().filter(q -> q.getQueue().equals(queue)).findFirst();
        return queueInfoOpt.orElse(null);
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
        // Find and update the queue in the master list
        for (int i = 0; i < queues.size(); i++) {
            if (queues.get(i).getQueue().equals(queue.getQueue())) {
                queues.set(i, queue);
                break;
            }
        }

        // Reapply filters to update display
        applyFilters();
    }

    public void showProgress(String message) {
        GridData progressPanelData = (GridData) progressPanel.getLayoutData();
        // progressPanelData.exclude = false;
        progressPanel.setVisible(true);
        progressLabel.setText(message);
        layout(true);
    }

    public void hideProgress() {
        GridData progressPanelData = (GridData) progressPanel.getLayoutData();
        // progressPanelData.exclude = true;
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

        // Compile pattern using smart pattern compiler
        // Automatically detects and converts IBM MQ wildcards (*, ?) to Java regex
        Pattern pattern = null;
        if (!regexPattern.isEmpty()) {
            try {
                pattern = QueueNameRegexCalculator.compileSmartPattern(regexPattern, true);
                regexFilterText.setBackground(null);  // Clear error indicator
            } catch (PatternSyntaxException e) {
                // Invalid regex - show error and display all queues
                regexFilterText.setBackground(getDisplay().getSystemColor(SWT.COLOR_RED));
                filteredQueues.addAll(queues);
                sortQueues();
                refresh();
                return;
            } catch (IllegalArgumentException e) {
                // Empty pattern - shouldn't happen but handle gracefully
                regexFilterText.setBackground(getDisplay().getSystemColor(SWT.COLOR_RED));
                filteredQueues.addAll(queues);
                sortQueues();
                refresh();
                return;
            }
        }

        // Apply filters using streams
        final Pattern finalPattern = pattern;
        filteredQueues.addAll(
                queues.stream()
                        .filter(q -> finalPattern == null || finalPattern.matcher(q.getQueue()).find())
                        .filter(q -> minDepth == 0 || q.getCurrentDepth() >= minDepth)
                        .toList()
        );

        sortQueues();
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

    private void sortBy(int columnIndex) {
        if (sortColumn == columnIndex) {
            // Toggle sort direction
            sortAscending = !sortAscending;
        } else {
            // New column, default to ascending
            sortColumn = columnIndex;
            sortAscending = true;
        }

        // Update sort indicator
        table.setSortColumn(table.getColumn(columnIndex));
        table.setSortDirection(sortAscending ? SWT.UP : SWT.DOWN);

        // Apply sorting
        sortQueues();
        refresh();
    }

    private void sortQueues() {
        Comparator<QueueInfo> comparator = switch (sortColumn) {
            case 0 -> Comparator.comparing(q -> q.getLabel() != null ? q.getLabel() : "", String.CASE_INSENSITIVE_ORDER);
            case 1 -> Comparator.comparing(QueueInfo::getQueue);
            case 2 -> Comparator.comparingInt(QueueInfo::getCurrentDepth);
            case 3 -> Comparator.comparingInt(QueueInfo::getMaxDepth);
            case 4 -> Comparator.comparingDouble(QueueInfo::getDepthPercentage);
            default -> Comparator.comparing(QueueInfo::getQueue);
        };

        if (!sortAscending) {
            comparator = comparator.reversed();
        }

        filteredQueues.sort(comparator);
    }
}
