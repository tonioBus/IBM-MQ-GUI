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
import com.aquila.ibm.mq.gui.util.SequentialQueueRefreshScheduler;
import lombok.Getter;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
public class QueueListViewer extends Composite {

    public void refreshSort() {
        this.sortBy(3);
    }

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

    private Text regexFilterText;
    private Spinner depthFilterSpinner;
    private Label filterStatusLabel;

    // Auto-refresh controls
    private Button autoRefreshButton;
    private Combo refreshIntervalCombo;
    /**
     * -- GETTER --
     *  Get auto-refresh enabled state
     */
    @Getter
    private boolean autoRefreshEnabled = false;
    @Setter
    private AutoRefreshListener autoRefreshListener;

    // Sequential refresh scheduler
    private SequentialQueueRefreshScheduler sequentialScheduler;
    private Function<String, QueueInfo> singleQueueFetcher;

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

        addDisposeListener(e -> {
            sequentialScheduler.shutdown();
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
        regexFilterText.addListener(SWT.Modify, e -> applyFilters(false));

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
        depthFilterSpinner.addListener(SWT.Selection, e -> applyFilters(false));

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
        Button refreshNowButton = new Button(panel, SWT.PUSH);
        refreshNowButton.setText("Refresh Now");
        refreshNowButton.setToolTipText("Refresh immediately queues information");
        refreshNowButton.addListener(SWT.Selection, e -> {
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
            int interval = getSelectedRefreshInterval();
            if (autoRefreshEnabled) {
                sequentialScheduler.updateDelay(interval);
            }
            if (autoRefreshListener != null) {
                autoRefreshListener.onRefreshIntervalChanged(interval);
            }
        });

        // Auto-refresh toggle button
        autoRefreshButton = new Button(panel, SWT.TOGGLE);
        autoRefreshButton.setText("Auto OFF");
        autoRefreshButton.setToolTipText("Toggle automatic refresh of queues information");
        autoRefreshButton.addListener(SWT.Selection, e -> {
            autoRefreshEnabled = autoRefreshButton.getSelection();
            updateAutoRefreshButtonState();

            if (autoRefreshEnabled) {
                startSequentialRefresh();
            } else {
                stopSequentialRefresh();
            }

            if (autoRefreshListener != null) {
                autoRefreshListener.onAutoRefreshToggled(autoRefreshEnabled);
            }
        });
    }

    public void refreshDynamicFields(QueueInfo queueInfo) {
        Display.getDefault().asyncExec(() -> {
            Arrays.stream(table.getItems()).toList().stream().filter(t -> t.getText(0).equals(queueInfo.getLabel())).findFirst().ifPresent(item -> {
                updateOneQueue(queueInfo, item);
            });
        });
    }

    private void updateOneQueue(QueueInfo queueInfo, TableItem item) {
        item.setText(2, String.valueOf(queueInfo.getCurrentDepth()));
        item.setText(3, String.valueOf(queueInfo.getMaxDepth()));
        item.setText(4, String.format("%.1f%%", queueInfo.getDepthPercentage()));

        ThresholdConfig.AlertLevel alertLevel = alertManager.getCurrentAlertLevel(queueInfo.getQueue());

        switch (alertLevel) {
            case CRITICAL -> item.setBackground(redColor);
            case WARNING -> item.setBackground(yellowColor);
            default -> {
                if (queueInfo.getCurrentDepth() == 0) {
                    item.setBackground(null);
                } else {
                    item.setBackground(greenColor);
                }
            }
        }
    }

    public void refreshSelectedQueue() {
        final QueueInfo selectedQueue = getSelectedQueue();
        if (selectedQueue != null) {
            log.info("Refreshing selected queue: {}", selectedQueue.getQueue());
            if (singleQueueFetcher != null) {
                final TableItem[] selectionItem = table.getSelection();
                if (selectionItem != null && selectionItem.length > 0) {
                    updateOneQueue(singleQueueFetcher.apply(selectedQueue.getQueue()), selectionItem[0]);
                }
            }
        }
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

    public void setQueues(List<QueueInfo> queues, boolean forceRefresh) {
        this.queues.clear();
        this.queues.addAll(queues);
        Display.getDefault().asyncExec(() -> {
            applyFilters(forceRefresh);
            // applyFilters(false);
        });
    }

    public void refresh(boolean force) {
        if( force) log.info("Refresh force:{} #######", force, new RuntimeException("DEBUG"));
        final int oldSelection = table.getSelectionIndex();
        if(force) {
            table.removeAll();
            for (QueueInfo queue : filteredQueues) {
                TableItem item = new TableItem(table, SWT.NONE);
                updateTableItem(item, queue);
            }
        }

        updateFilterStatus();

        if (!filteredQueues.isEmpty() && table.getSelectionIndex() < 0) {
            if (table.getSelectionIndex() == -1) {
                log.info("Table Selection: {}", oldSelection);
                table.select(oldSelection);
                if (oldSelection >= 0 && oldSelection < filteredQueues.size() && selectionListener != null) {
                    selectionListener.accept(filteredQueues.get(oldSelection));
                }
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
        if (tableItem.isEmpty()) {
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
        applyFilters(false);
    }

    private void applyFilters(boolean forceRefresh) {
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
                if(forceRefresh) sortQueues();
                refresh(true);
                return;
            } catch (IllegalArgumentException e) {
                // Empty pattern - shouldn't happen but handle gracefully
                regexFilterText.setBackground(getDisplay().getSystemColor(SWT.COLOR_RED));
                filteredQueues.addAll(queues);
                sortQueues();
                refresh(true);
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
        refresh(forceRefresh);
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
        refresh(false);
    }

    private void sortQueues() {
        Comparator<QueueInfo> comparator = switch (sortColumn) {
            case 0 ->
                    Comparator.comparing(q -> q.getLabel() != null ? q.getLabel() : "", String.CASE_INSENSITIVE_ORDER);
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

    /**
     * Set the function that fetches a single queue's information via PCF
     *
     * @param fetcher Function that takes queue name and returns QueueInfo
     */
    public void setSingleQueueFetcher(Function<String, QueueInfo> fetcher) {
        this.singleQueueFetcher = fetcher;
        sequentialScheduler.setQueueFetcher(fetcher);
    }

    /**
     * Start sequential refresh of queues
     */
    private void startSequentialRefresh() {
        if (singleQueueFetcher == null) {
            log.warn("Cannot start sequential refresh: queue fetcher not set");
            return;
        }

        List<String> queueNames = queues.stream()
                .map(QueueInfo::getQueue)
                .toList();

        if (queueNames.isEmpty()) {
            log.warn("Cannot start sequential refresh: no queues to refresh");
            return;
        }

        int delay = getSelectedRefreshInterval();
        sequentialScheduler.start(queueNames, delay);
        log.info("Sequential refresh started: {} queues, {}ms delay", queueNames.size(), delay);
    }

    /**
     * Stop sequential refresh
     */
    private void stopSequentialRefresh() {
        if(sequentialScheduler!=null) sequentialScheduler.stop();
        log.info("Sequential refresh stopped");
    }

    /**
     * Update queue list for sequential refresh (called when queues change)
     */
    public void updateSequentialRefreshQueueList() {
        if (autoRefreshEnabled && sequentialScheduler.isRunning()) {
            List<String> queueNames = queues.stream()
                    .map(QueueInfo::getQueue)
                    .toList();
            sequentialScheduler.updateQueueList(queueNames);
        }
    }

    /**
     * Callback when a queue is updated from the scheduler
     */
    private void updateQueueFromScheduler(QueueInfo updatedQueue, String queueName) {
        if (updatedQueue == null) {
            return;
        }

        // Find and update the queue in the master list
        for (int i = 0; i < queues.size(); i++) {
            if (queues.get(i).getQueue().equals(queueName)) {
                queues.set(i, updatedQueue);
                break;
            }
        }

        // Update only the affected row in the table (more efficient than full refresh)
        for (int i = 0; i < filteredQueues.size(); i++) {
            if (filteredQueues.get(i).getQueue().equals(queueName)) {
                filteredQueues.set(i, updatedQueue);

                // Update the table item
                if (i < table.getItemCount()) {
                    TableItem item = table.getItem(i);
                    updateTableItem(item, updatedQueue);
                }
                break;
            }
        }
    }

    /**
     * Callback when an error occurs during queue refresh
     */
    private void handleQueueRefreshError(String queueName, Exception exception) {
        log.error("Error refreshing queue {}: {}", queueName, exception.getMessage());
        // Could show a status indicator or notification here if needed
    }

    /**
     * Set auto-refresh state programmatically
     */
    public void setAutoRefreshEnabled(boolean enabled) {
        if (autoRefreshEnabled != enabled) {
            autoRefreshButton.setSelection(enabled);
            autoRefreshEnabled = enabled;
            updateAutoRefreshButtonState();

            if (enabled) {
                startSequentialRefresh();
            } else {
                stopSequentialRefresh();
            }
        }
    }
}
